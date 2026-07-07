# Linked Lists — Fundamentals

> **What you'll learn:** How linked lists work under the hood, master pointer manipulation patterns (dummy-head insertion, reversal, slow-fast pointers), and solve cycle detection, reordering, and deletion problems that appear in every FAANG interview loop.

> **Audience:** You know arrays. Now learn linked lists: why pointer-manipulation matters, when each of 5 core patterns applies, and how to avoid the 5 silent bugs that pass compilation but produce wrong answers.

---

> **Curriculum alignment:** This deep-dive synthesizes:
> - **Striver's Linked List Series** (7+ videos: reverse LL, cycle detection, find middle, slow-fast pointers, reorder LL, flattening)
> - **LeetCode Problem Editorials** (LC 206 Reverse, LC 141 Cycle, LC 143 Reorder, LC 142 Cycle Start, LC 19 Remove Nth, LC 2 Add Two Numbers)
> - **GeeksforGeeks linked-list fundamentals** (pointer manipulation, dummy-node pattern, Floyd's cycle algorithm)
>
> **Credit:** Pointer-motion patterns (dummy head, three-pointer reversal, slow-fast) and walkthroughs adapted from Striver. Problem-driven examples from LeetCode editorials. Pattern Application Gallery (most-asked interview problems per pattern) and FAANG interview context are this doc's contribution.

---

## 🎯 Why You're Reading This

After reading this, you will:

1. **Build intuition** around pointer motion — why dummy heads exist, how slow-fast pointers find midpoints, what "three pointers" really means
2. **Master 5 core patterns** that cover ~85% of linked-list interview problems: dummy-head insertion, reversal, slow-fast (cycle/middle), cycle-start detection, k-gap pointer
3. **Solve 15+ interview problems** using these patterns (3-4 most-asked per pattern)
4. **Recognize when to use which pattern** — walkthroughs show you the decision tree
5. **Understand why your code breaks** — gotchas section covers the 5 silent bugs that pass compilation
6. **Know what to attempt now vs. bookmark** — difficulty tags guide your practice order

By the end, LC 206 (Reverse), LC 141 (Cycle), LC 142 (Cycle Start), and LC 143 (Reorder) will feel like natural applications, not mysterious algorithms.

---

## 🚦 Difficulty Tagging — Read Before You Pick a Problem

| Tag | Meaning | Action |
| --- | --- | --- |
| ✅ **Try Now** | Solvable with concepts covered up to this point | Open LeetCode, attempt cold, time-box ~25 min |
| 🟡 **Try After [Section X]** | Needs concepts from a later section in this same doc | Bookmark, return after the named section |
| 🔴 **Reference Only** | Needs concepts beyond this doc (e.g., recursion, stack unwinding) | Read problem + editorial; don't attempt cold |

---

## 🌲 Definition — What Is a Linked List?

A **linked list** is a linear data structure where each element (called a **node**) contains:
- A **value** (the data you care about)
- A **next pointer** pointing to the next node (or `null` if last)

Unlike arrays (contiguous memory, O(1) random access), linked lists offer:
- **O(1) insertion/deletion at a known position** (no shifting)
- **O(n) search** (must traverse from head)

**Simplest example:**

```
1 -> 2 -> 3 -> null
```

Each arrow represents a `next` pointer. To find the value 2, you start at node 1, follow its `next` pointer, and arrive at node 2.

---

## 📖 Terminology Table

| Term | Meaning | Interview context |
| --- | --- | --- |
| **Node** | One element in the list; contains a value and a `next` pointer | "Insert node", "traverse nodes" |
| **Head** | The first node of the list; your entry point | "Return the head", "head is null" |
| **Tail** | The last node; its `next` is `null` | "Tail of the list" |
| **Dummy node** | A synthetic first node (value = 0 or irrelevant) that simplifies insertion/deletion logic | "Use dummy to handle head insertion" |
| **Fast pointer** | A pointer that moves 2 steps per iteration | "Fast moves 2, slow moves 1" |
| **Slow pointer** | A pointer that moves 1 step per iteration | "Find middle with slow-fast" |
| **Cycle** | A scenario where pointers eventually loop (node.next points to an earlier node) | "Detect cycle", "find cycle start" |
| **Orphaned segment** | Nodes that are no longer reachable from the head; garbage collector reclaims them | "Don't orphan the list" |

---

## 🛠️ ListNode Class Definition

Every linked-list problem assumes you have a `ListNode` class (already provided on LeetCode):

```java
public class ListNode {
    int val;
    ListNode next;

    ListNode(int val) {
        this.val = val;
        this.next = null;
    }

    ListNode(int val, ListNode next) {
        this.val = val;
        this.next = next;
    }
}
```

**Never redefine this on LeetCode.** Use it as-is.

---

## 🧠 Mental Model — Pointers as Hands

**The big idea:** Linked-list manipulation is like **handing off the list to the next person in a relay race**. Your hand (pointer) must always know where you came from, where you are, and where you're going next.

### Worked example: Reversing `1 -> 2 -> 3 -> null`

Imagine you have three pointers: `prev`, `curr`, `next`.

- **Start:** `prev = null`, `curr = 1`, `next = 2`
- **Step 1:** Save `next` (because you're about to break the current link). Reverse the link: `curr.next = prev` (node 1 now points backward to `null`).
  - After: `prev = 1`, `curr = 2`, `next = 3`
- **Step 2:** Same logic. `curr.next = prev` makes node 2 point to node 1.
  - After: `prev = 2`, `curr = 3`, `next = null`
- **Step 3:** `curr.next = prev` makes node 3 point to node 2.
  - After: `prev = 3`, `curr = null`
- **Result:** Head is now `prev` (the last node encountered), and the list is reversed: `3 -> 2 -> 1 -> null`.

**Why you need `next`?** If you don't save `next` before reversing the link, you've orphaned the rest of the list — you can't reach it anymore.

---

## 🎨 Visual — Core Pointer Patterns

```
Pattern 1: Dummy-Head Insertion
─────────────────────────────────
dummy(0) -> A -> B -> C -> null
 ↑
Insert X after B:
dummy(0) -> A -> B -> X -> C -> null
Return dummy.next = A

---

Pattern 2: Three-Pointer Reversal
──────────────────────────────────
Initial:     1 -> 2 -> 3 -> null
            (curr)

Step 1:      null <- 1    2 -> 3 -> null
            (prev) (curr)

Step 2:      null <- 1 <- 2    3 -> null
                      (prev)  (curr)

Step 3:      null <- 1 <- 2 <- 3
                              (prev/new head)

KEY INVARIANT: At each step, (prev...curr-1) is reversed, (curr...tail) is unreversed.
               When curr == null, all nodes are reversed.

---

Pattern 3: Slow-Fast Pointer (Middle Finding)
──────────────────────────────────────────────
List: 1 -> 2 -> 3 -> 4 -> 5

Step 1: slow=1, fast=1
Step 2: slow=2, fast=3
Step 3: slow=3, fast=5
Step 4: slow=4, fast=null
        (slow at middle=3 for odd length)

---

Pattern 4: Cycle Detection & Start
───────────────────────────────────
Cycle:  A -> B -> C -> D -> C -> D -> ...
                 ↑_________↑
           Meet here (Floyd's algorithm)

Then reset one pointer to head, move both 1 step at a time:
Head -> ... -> C -> D -> C -> ...
↑              ↑
Both start here, move in tandem
Eventually meet at C (cycle start)

---

Pattern 5: K-Gap Pointer (Kth from End)
────────────────────────────────────────
k=2: dummy -> A -> B -> C -> D -> null
      ↑        ↑
    left      right (gap=2)

Move both until right=null:
dummy -> A -> B -> C -> D -> null
              ↑            ↑
            left         right

Remove left.next (C): A -> B -> D
```

---

## 🎨 Style Habits — Build These From Day 1

### 🌐 Universal Habits (apply everywhere)

#### Habit 1 — Always handle the null case first

When traversing or building a linked list, check `curr != null` **before** accessing `curr.val` or `curr.next`.

```java
// ❌ Wrong — can throw NPE if curr is null
while (curr.next != null) {
    curr = curr.next;
}

// ✅ Right — check curr first
while (curr != null) {
    if (curr.next == null) break; // tail reached
    curr = curr.next;
}
```

**Why?** Linked lists have a natural terminator (`null`). Miss it, and your code crashes.

---

#### Habit 2 — Always save the next pointer before mutation

If you're about to change `curr.next`, save `curr.next` in a temporary variable first.

```java
// ❌ Wrong — orphans the rest of the list
ListNode temp = curr.next.next; // but curr.next has changed!
curr.next = prev;

// ✅ Right — save next before changing
ListNode next = curr.next;
curr.next = prev;
curr = next;
```

**Why?** Mutating `curr.next` breaks the chain. If you don't save the next pointer before the mutation, you lose access to the rest of the list.

---

#### Habit 3 — Always use a dummy node when inserting at the head

Inserting at the head is tricky (you need to return a new head). A dummy node eliminates this special case.

```java
// ❌ Wrong — have to handle head insertion separately
if (insertAfter(someNode, value)) {
    head = someNode; // special case
}

// ✅ Right — dummy makes all insertions uniform
ListNode dummy = new ListNode(0, head);
insertAfter(dummy, value); // insert after dummy (same logic as anywhere else)
return dummy.next; // dummy.next is the new head
```

**Why?** Without a dummy, your insertion logic differs for head vs. other nodes. With a dummy, insertion is uniform everywhere, reducing bugs.

---

#### Habit 4 — Write a small `printList` helper for testing

```java
void printList(ListNode head) {
    while (head != null) {
        System.out.print(head.val + " -> ");
        head = head.next;
    }
    System.out.println("null");
}
```

Use this in every problem to visualize state and catch bugs immediately.

---

### 🔧 Context-Specific Habits

#### Habit 5 — Slow-fast pointer: check the fast pointer's boundary

When advancing the fast pointer, check `fast != null && fast.next != null` to avoid NPE.

```java
// ❌ Wrong — fast.next will NPE if fast is null
while (fast.next != null) {
    fast = fast.next.next; // NPE if fast.next is null
}

// ✅ Right — check fast and fast.next
while (fast != null && fast.next != null) {
    slow = slow.next;
    fast = fast.next.next;
}
```

**Why?** Fast pointer moves 2 steps. At the boundary, `fast` might be `null` or `fast.next` might be `null`. Both must be checked before dereferencing.

---

#### Habit 6 — Cycle detection: prevent infinite loops

When detecting a cycle, always cap your loop with null checks. Never rely on convergence alone.

```java
// ✅ Right — fast-slow will meet if cycle exists
ListNode slow = head, fast = head;
while (slow != null && fast != null && fast.next != null) {
    slow = slow.next;
    fast = fast.next.next;
    if (slow == fast) {
        return true; // cycle found
    }
}
return false;
```

**Why?** If there's a cycle, slow and fast will eventually collide. If there's no cycle, fast reaches `null`. This is safe by mathematical guarantee (Floyd's algorithm).

---

> **Quick recap of universal habits:** Always null-check → save next before mutation → use dummy for insertion → write printList helper. Slow-fast pointer: check boundaries. Cycle detection: cap the loop.

---

## 🔨 Setup — Phase 1 Before the Pointer Loop

> **The Phase 1 question for linked lists:** *Before I write the pointer loop, do I need a dummy head? How many pointers? What are their starting positions?* The most common linked-list bugs are not algorithmic — they are setup failures: forgetting `next = curr.next` before flipping an arrow, or not using a dummy when the head itself might be deleted.

### Setup Decision Table

| Setup decision | When to use it | What it unlocks | Common mistake |
| --- | --- | --- | --- |
| **Dummy head node** | Inserting or deleting nodes that might be the head; building a new list from scratch | Avoids null-check for the first node; `dummy.next` is always the result head | Not using dummy when head deletion is possible — code needs a fragile special case |
| **Three pointers: prev / curr / next** | Reversing a list or sublist in-place | O(1)-space reversal of any length | Forgetting `next = curr.next` BEFORE `curr.next = prev` — overwrites the only reference to the rest of the list |
| **Two pointers: slow / fast** | Finding midpoint, detecting a cycle | Finds mid in one pass; Floyd's cycle detection in O(1) space | Not checking `fast != null && fast.next != null` before advancing — NPE on even-length or cycle-free lists |
| **Fixed-gap dual pointers** | kth node from end; find the node just before the deletion target | Eliminates the need to know list length | Gap must be `n + 1` (not `n`) for removal — this positions left at the node **before** the target |
| **Null / single-node guard** | Any algorithm where `head.next` is accessed on the first line | Prevents NPE on empty or single-element input | Checking only `head != null` but calling `head.next.val` — always guard both |

### Phase 1 Code Stubs — Paste Before the Algorithm

**Dummy head (any new list construction or head-deletion risk):**

```java
// Phase 1 — dummy avoids special-casing the first node
ListNode dummy = new ListNode(0);
dummy.next = head;
ListNode curr = dummy;
// build output list via curr.next = newNode; curr = curr.next;
// always return dummy.next, not head
return dummy.next;
```

**Three-pointer reversal setup:**

```java
// Phase 1 — initialize BEFORE any mutation
ListNode prev = null;
ListNode curr = head;
// inside loop — ALWAYS in this order:
//   ListNode next = curr.next;   ← save before breaking the link
//   curr.next = prev;            ← flip the arrow
//   prev = curr;                 ← advance prev
//   curr = next;                 ← advance curr
```

**Slow / fast midpoint setup:**

```java
// Phase 1 — fast one ahead so slow stops at first-half tail on split
ListNode slow = head;
ListNode fast = head.next;
while (fast != null && fast.next != null) {
    slow = slow.next;
    fast = fast.next.next;
}
// slow is now the midpoint (first-half tail); slow.next is second-half head
ListNode secondHalf = slow.next;
slow.next = null;   // split the list
```

**Fixed-gap kth-from-end setup (n + 1 gap):**

```java
// Phase 1 — dummy + gap of n+1 leaves left one node BEFORE the target
ListNode dummy = new ListNode(0);
dummy.next = head;
ListNode left = dummy;
ListNode right = dummy;
for (int i = 0; i <= n; i++) {
    right = right.next;
}
// advance both until right == null; then left.next is the node to remove
```

**Null / single-node guard:**

```java
// Phase 1 — add at the very top of any linked-list method
if (head == null || head.next == null) {
    return head;
}
```

### Pre-Flight Checklist

```
Before writing the pointer loop, answer:
  □ Could head be deleted or replaced?       → use dummy node; return dummy.next
  □ Am I reversing in-place?                 → prev=null, curr=head; save next BEFORE flipping
  □ Do I need the midpoint?                  → slow=head, fast=head.next; guard fast≠null && fast.next≠null
  □ Do I need kth from end?                  → gap = n+1 from dummy; left ends one before the target
  □ Empty or single-node input possible?     → guard: if (head == null || head.next == null) return head
```

---

## 🧭 Patterns — 5 Core Techniques

### Pattern 1 — Dummy-Head Node Insertion

**When you'll see this pattern:**
- LC 206 Reverse — reversing alters the head pointer
- LC 25 Reverse Nodes in K-Group — reversing sublists
- LC 21 Merge Two Sorted Lists — merging at head
- Real-world example: Insert nodes while maintaining list structure

**Problem motivation — concrete example:**

"Given the head of a linked list and a value to insert, insert a new node with that value at a specific position while handling the case where we insert before the head."

Example: Insert 99 before node with value 5 in list `1 -> 2 -> 5 -> 3`

**Naive approach (and why it fails):**

```java
// Brute: handle head insertion separately, other positions differently
// Code structure: if (position == 0) { head = new ListNode(...); } else { ... }
// Problem: Two different code paths = two chances to mess up, hard to maintain
```

**Why this pattern solves it:**

Create a dummy node pointing to the head. All insertions become uniform — insert after a node using the same logic everywhere. **Key insight: dummy absorbs the "special case" of head insertion.**

**Steps in plain English:**

1. Create a dummy node pointing to the head (`dummy.next = head`).
2. Traverse to the insertion point.
3. Insert the new node by rewiring pointers.
4. Return dummy.next as the new head.

```java
// Dummy-head insertion pattern
ListNode dummy = new ListNode(0, head);
ListNode curr = dummy;

// Traverse to insertion point
for (int i = 0; i < position && curr != null; i++) {
    curr = curr.next;
}

// Insert new node (uniform logic for all positions)
if (curr != null) {
    ListNode newNode = new ListNode(value);
    newNode.next = curr.next;
    curr.next = newNode;
}

// Return dummy.next as the new head
return dummy.next;
```

**Why this works:** Dummy eliminates the special case. All insertions follow the same pattern.

**🎨 Visual — Dummy Node: The Problem It Solves**

```
WITHOUT dummy — two code paths for the same job:

  head ──► [1] ──► [2] ──► [3] ──► null

  Insert 99 at FRONT:
    special case → head = new Node(99); head.next = oldHead

  Insert 99 after node [1]:
    normal case  → newNode.next = node1.next; node1.next = newNode

  Different code for the same operation = two bugs waiting to happen.

WITH dummy — one code path for every position:

  dummy[0] ──► [1] ──► [2] ──► [3] ──► null
      ↑
     curr

  Insert 99 at "front" (= after dummy):

    newNode.next = curr.next   →  newNode.next = [1]
    curr.next    = newNode     →  dummy.next   = [99]

    dummy[0] ──► [99] ──► [1] ──► [2] ──► [3] ──► null

  Insert 99 after node [1]:

    (advance curr to [1])
    newNode.next = curr.next   →  newNode.next = [2]
    curr.next    = newNode     →  [1].next     = [99]

    dummy[0] ──► [1] ──► [99] ──► [2] ──► [3] ──► null

  EXACT SAME CODE for both positions.
  Always return dummy.next — that is the real head of your result list.

KEY INVARIANT: dummy.next is ALWAYS the true head of the list.
               dummy absorbs "what if the head changes?" so your loop never has to.
```

---

> 🧩 **Drill:**
> Write dummy-based insertion that adds a node with value 99 **before the first node with value 5**. Include null-check and return statement.

<details>
<summary>Solution</summary>

```java
ListNode dummy = new ListNode(0, head);
ListNode curr = dummy;
while (curr != null && curr.next != null) {
    if (curr.next.val == 5) {
        ListNode newNode = new ListNode(99);
        newNode.next = curr.next;
        curr.next = newNode;
        break;
    }
    curr = curr.next;
}
return dummy.next;
```
</details>

---

### Pattern 1 — Pattern Application Gallery

**Problem 1a: LC 206 Reverse Linked List** (covered in Pattern 2 walkthrough)

**Problem 1b: LC 25 Reverse Nodes in K-Group**

**Problem:** Reverse every k consecutive nodes and return the modified list.

**The insight:** Use dummy, find k-node boundaries, reverse each group, reconnect.

**Structure:**
```java
class Solution {
    public ListNode reverseKGroup(ListNode head, int k) {
        ListNode dummy = new ListNode(0, head);
        ListNode prevGroup = dummy;
        while (true) {
            ListNode kth = getKth(prevGroup, k);
            if (kth == null) {
                break;
            }
            ListNode nextGroup = kth.next;
            ListNode[] reversed = reverseGroup(prevGroup.next, nextGroup);
            prevGroup.next = reversed[0];
            prevGroup = reversed[1];
        }
        return dummy.next;
    }
    
    private ListNode getKth(ListNode node, int k) {
        for (int i = 0; i < k && node != null; i++) {
            node = node.next;
        }
        return node;
    }
    
    private ListNode[] reverseGroup(ListNode head, ListNode tail) {
        ListNode prev = tail;
        ListNode curr = head;
        while (curr != tail) {
            ListNode next = curr.next;
            curr.next = prev;
            prev = curr;
            curr = next;
        }
        return new ListNode[]{prev, head};
    }
}
```

**Time:** O(n), **Space:** O(1)

---

**Problem 1c: LC 21 Merge Two Sorted Lists**

**Problem:** Merge two sorted linked lists into one sorted list.

**The insight:** Use dummy, compare nodes from both lists, attach smaller one.

**Structure:**
```java
class Solution {
    public ListNode mergeTwoLists(ListNode list1, ListNode list2) {
        ListNode dummy = new ListNode(0);
        ListNode curr = dummy;
        while (list1 != null && list2 != null) {
            if (list1.val <= list2.val) {
                curr.next = list1;
                list1 = list1.next;
            } else {
                curr.next = list2;
                list2 = list2.next;
            }
            curr = curr.next;
        }
        curr.next = list1 != null ? list1 : list2;
        return dummy.next;
    }
}
```

**Time:** O(m + n), **Space:** O(1)

---

### Pattern 2 — Three-Pointer Reversal

**When you'll see this pattern:**
- LC 206 Reverse Linked List — reverse entire list
- LC 92 Reverse Linked List II — reverse a subrange
- LC 143 Reorder List — reverse second half to enable merging
- Real-world example: Undo operations (reverse order of changes)

**Problem motivation — concrete example:**

"Given the head of a linked list, reverse it and return the head of the reversed list."

Example: `1 -> 2 -> 3 -> null` becomes `3 -> 2 -> 1 -> null`

**Naive approach (and why it fails):**

```java
// Brute: use recursion or extra space
// Recursive: reverse rest, then fix pointers (hard to reason about)
// Stack: push all nodes, pop and rebuild (O(n) space)
// Better: three pointers, no recursion, O(1) space
```

**Why this pattern solves it:**

Maintain three pointers: `prev` (reversed portion), `curr` (current node), `next` (temp for next unvisited node). At each step, reverse the link and advance all three. **Key insight: save `next` before mutating `curr.next` to avoid orphaning the list.**

**Steps in plain English:**

1. Initialize three pointers: `prev = null`, `curr = head`, `next = null`.
2. While `curr != null`:
   - Save `next = curr.next` (before we mutate).
   - Reverse the link: `curr.next = prev`.
   - Advance: `prev = curr`, `curr = next`.
3. Return `prev` as the new head.

```java
// Three-pointer reversal pattern
ListNode prev = null;
ListNode curr = head;
ListNode next = null;

while (curr != null) {
    // Step 1: save next pointer before mutation
    next = curr.next;
    
    // Step 2: reverse the link
    curr.next = prev;
    
    // Step 3: advance pointers
    prev = curr;
    curr = next;
}

// Return prev (new head)
return prev;
```

**Why this works:** By saving `next` before mutating `curr.next`, you never orphan the list. `prev` accumulates the reversed portion.

**🎨 Visual — Three-Pointer Reversal: `1 → 2 → 3 → null`**

```
START:   prev=null  curr=[1]

         null        [1] ──► [2] ──► [3] ──► null
         prev        curr

STEP 1:  next = curr.next = [2]
         curr.next = prev  →  [1] now points left (arrow flipped)
         prev = [1],  curr = [2]

         null ◄── [1]    [2] ──► [3] ──► null
                  prev   curr

STEP 2:  next = curr.next = [3]
         curr.next = prev  →  [2] now points to [1]
         prev = [2],  curr = [3]

         null ◄── [1] ◄── [2]    [3] ──► null
                           prev   curr

STEP 3:  next = curr.next = null
         curr.next = prev  →  [3] now points to [2]
         prev = [3],  curr = null  ← loop exits

         null ◄── [1] ◄── [2] ◄── [3]
                                    prev  (new head!)

return prev  →  3 ──► 2 ──► 1 ──► null  ✅

KEY INVARIANT: At every step, [head..prev] is fully reversed;
               [curr..tail] is still in original forward order.
               When curr=null, entire list is reversed. New head = prev.
```

---

> 🧩 **Drill:**
> Write three-pointer reversal **from memory**. Time yourself: <2 min = muscle memory, 2-4 min = learning, >4 min = re-read.

<details>
<summary>Solution</summary>

```java
ListNode prev = null;
ListNode curr = head;
while (curr != null) {
    ListNode next = curr.next;
    curr.next = prev;
    prev = curr;
    curr = next;
}
return prev;
```
</details>

---

### Pattern 2 — Pattern Application Gallery

**Problem 2a: LC 206 Reverse Linked List**

**Problem:** Reverse entire linked list.

**Naive approach:**
```java
// Recursive: difficult to reason about, O(n) call stack space
// Stack: push all nodes, pop and rebuild, O(n) extra space
// Three-pointer: O(1) space, O(n) time, optimal
```

**The insight:** Three pointers avoid extra space and recursion.

**Structure:**
```java
class Solution {
    public ListNode reverseList(ListNode head) {
        ListNode prev = null;
        ListNode curr = head;
        while (curr != null) {
            ListNode next = curr.next;
            curr.next = prev;
            prev = curr;
            curr = next;
        }
        return prev;
    }
}
```

**Time:** O(n), **Space:** O(1)

---

**Problem 2b: LC 92 Reverse Linked List II**

**Problem:** Reverse a subrange of the linked list (from position left to right).

**The insight:** Find the boundary, reverse the subrange, reconnect.

**Structure:**
```java
class Solution {
    public ListNode reverseBetween(ListNode head, int left, int right) {
        ListNode dummy = new ListNode(0, head);
        ListNode prevLeft = dummy;
        for (int i = 0; i < left - 1 && prevLeft != null; i++) {
            prevLeft = prevLeft.next;
        }
        ListNode curr = prevLeft.next;
        for (int i = 0; i < right - left && curr != null; i++) {
            ListNode next = curr.next;
            curr.next = next.next;
            next.next = prevLeft.next;
            prevLeft.next = next;
        }
        return dummy.next;
    }
}
```

**Time:** O(n), **Space:** O(1)

---

**Problem 2c: LC 2 Add Two Numbers**

**Problem:** Add two numbers represented as linked lists (digits in reverse order).

**The insight:** Traverse both lists, add digits with carry, construct result.

**Structure:**
```java
class Solution {
    public ListNode addTwoNumbers(ListNode l1, ListNode l2) {
        ListNode dummy = new ListNode(0);
        ListNode curr = dummy;
        int carry = 0;
        while (l1 != null || l2 != null || carry > 0) {
            int val1 = l1 != null ? l1.val : 0;
            int val2 = l2 != null ? l2.val : 0;
            int sum = val1 + val2 + carry;
            carry = sum / 10;
            curr.next = new ListNode(sum % 10);
            curr = curr.next;
            l1 = l1 != null ? l1.next : null;
            l2 = l2 != null ? l2.next : null;
        }
        return dummy.next;
    }
}
```

**Time:** O(max(m, n)), **Space:** O(max(m, n))

---

### Pattern 3 — Slow-Fast Pointer (Cycle & Middle)

**When you'll see this pattern:**
- LC 876 Middle of the Linked List — find middle for reordering
- LC 141 Linked List Cycle — detect if cycle exists
- LC 143 Reorder List — find middle to split and reverse
- Real-world example: Partitioning lists, detecting loops in data structures

**Problem motivation — concrete example:**

"Given a linked list, find the middle node. If the list has two middle nodes, return the second one."

Example: `1 -> 2 -> 3 -> 4 -> 5` → middle is `3`

**Naive approach (and why it fails):**

```java
// Brute: traverse entire list to count length, then traverse again to find middle
// Time: O(2n) = O(n), but two passes
// Better: slow-fast pointers, one pass
```

**Why this pattern solves it:**

Use two pointers: slow (1 step/iteration), fast (2 steps/iteration). When fast reaches the end, slow is at the middle. **Key insight: if slow moves x steps, fast moves 2x steps. When fast finishes, slow is at x = n/2 (approximately the middle).**

**Steps in plain English:**

1. Initialize: `slow = head`, `fast = head`.
2. While `fast != null && fast.next != null`:
   - `slow = slow.next` (1 step)
   - `fast = fast.next.next` (2 steps)
3. When loop ends, `slow` is at the middle.

```java
// Slow-fast pointer pattern (find middle)
ListNode slow = head;
ListNode fast = head;

while (fast != null && fast.next != null) {
    slow = slow.next;
    fast = fast.next.next;
}

// slow is now at the middle
return slow;
```

**Why this works:** Fast pointer moves 2x faster. When it reaches the end, slow has moved exactly n/2 steps (the middle).

---

> 🧩 **Drill:**
> Write hasCycle() **from memory**. Did you check `fast != null && fast.next != null`? Did you use `fast = fast.next.next`?

<details>
<summary>Solution</summary>

```java
ListNode slow = head;
ListNode fast = head;
while (fast != null && fast.next != null) {
    slow = slow.next;
    fast = fast.next.next;
    if (slow == fast) {
        return true;
    }
}
return false;
```
</details>

---

### Pattern 3 — Pattern Application Gallery

**Problem 3a: LC 876 Middle of the Linked List**

**Problem:** Find the middle node.

**Structure:**
```java
class Solution {
    public ListNode middleNode(ListNode head) {
        ListNode slow = head;
        ListNode fast = head;
        while (fast != null && fast.next != null) {
            slow = slow.next;
            fast = fast.next.next;
        }
        return slow;
    }
}
```

**Time:** O(n), **Space:** O(1)

---

**Problem 3b: LC 141 Linked List Cycle**

**Problem:** Detect if the list has a cycle.

**Structure:**
```java
class Solution {
    public boolean hasCycle(ListNode head) {
        ListNode slow = head;
        ListNode fast = head;
        while (fast != null && fast.next != null) {
            slow = slow.next;
            fast = fast.next.next;
            if (slow == fast) {
                return true;
            }
        }
        return false;
    }
}
```

**Time:** O(n), **Space:** O(1)

---

**Problem 3c: LC 143 Reorder List**

**Problem:** Reorder list as `L0 -> Ln -> L1 -> Ln-1 -> ...`.

**Naive approach:**
```java
// Brute: reverse and interleave (hard to get right in one pass)
// Better: find middle, reverse second half, merge
```

**The insight:** Slow-fast finds middle. Reverse second half. Merge two halves alternately.

**Structure:**
```java
class Solution {
    public void reorderList(ListNode head) {
        ListNode slow = head;
        ListNode fast = head.next;
        while (fast != null && fast.next != null) {
            slow = slow.next;
            fast = fast.next.next;
        }
        ListNode mid = slow.next;
        slow.next = null;
        ListNode reversed = reverse(mid);
        merge(head, reversed);
    }
    
    private ListNode reverse(ListNode head) {
        ListNode prev = null;
        while (head != null) {
            ListNode next = head.next;
            head.next = prev;
            prev = head;
            head = next;
        }
        return prev;
    }
    
    private void merge(ListNode l1, ListNode l2) {
        while (l2 != null) {
            ListNode l1Next = l1.next;
            ListNode l2Next = l2.next;
            l1.next = l2;
            l2.next = l1Next;
            l1 = l1Next;
            l2 = l2Next;
        }
    }
}
```

**Time:** O(n), **Space:** O(1)

---

### Pattern 4 — Find Cycle Start (Two-Pass)

**When you'll see this pattern:**
- LC 142 Linked List Cycle II — find where cycle begins
- Real-world example: Detecting loops and understanding entry point

**Problem motivation — concrete example:**

"Given a linked list, return the node where the cycle begins. If there's no cycle, return null."

Example: `1 -> 2 -> 3 -> 4 -> 2 (cycle)` → return node 2

**Naive approach (and why it fails):**

```java
// Brute: track visited nodes in a set, first repeat = cycle start
// Time: O(n), Space: O(n)
// Better: two-pass with Floyd's algorithm, O(1) space
```

**Why this pattern solves it:**

Detect cycle with slow-fast pointers. Reset one pointer to head, advance both at same speed. When they meet, that's the cycle start. **Key insight: mathematical property of Floyd's algorithm — distance from head to cycle start equals distance from meeting point to cycle start.**

**Steps in plain English:**

1. Detect cycle: Use slow-fast pointers to find a meeting point inside the cycle.
2. Reset: Set one pointer to head, keep the other at the meeting point.
3. Move in tandem: Both pointers advance 1 step at a time until they meet.
4. Collision point: That's the cycle start.

```java
// Cycle start detection pattern
ListNode slow = head;
ListNode fast = head;

// Step 1: detect cycle
while (fast != null && fast.next != null) {
    slow = slow.next;
    fast = fast.next.next;
    if (slow == fast) {
        break;  // cycle detected
    }
}

// If no cycle, return null
if (fast == null || fast.next == null) {
    return null;
}

// Step 2-4: reset one pointer to head, move in tandem
ListNode ptr1 = head;
ListNode ptr2 = slow;
while (ptr1 != ptr2) {
    ptr1 = ptr1.next;
    ptr2 = ptr2.next;
}

// Collision point is cycle start
return ptr1;
```

**Why this works:** Mathematical property. Both pointers traverse at same speed from this point onward, so they converge at the cycle start.

**🎨 Visual — Floyd's Cycle Start: Why Resetting Works**

```
List layout (m=3 tail-to-entry, cycle length L=4):

  head ──► [0] ──► [1] ──► [2] ──► [C] ──► [A] ──► [B] ──► [D]
                                     ▲                          │
                                     └──────────────────────────┘
  m = 3  (head → C)          k = 1  (C → meeting point A)

THE MATH:
  slow traveled:  m + k  =  3 + 1  =  4
  fast traveled:  2 × 4  =  8  =  m + k + r×L  =  4 + 1×4  ✓

  Rearrange:  r×L = m + k  →  m = r×L - k  =  1×4 - 1  =  3  ✓

WHAT m = r×L - k MEANS IN THE CYCLE:
  From meeting point A, walk m=3 steps forward inside the cycle:
    A ──► B  (step 1)
    B ──► D  (step 2)
    D ──► C  (step 3)  ← back at cycle entry!

  From head, walk m=3 steps:
    [0] ──► [1] ──► [2] ──► [C]  ← same destination!

RESET STEP:
  ptr1 = head         →  walks 3 steps: [0]→[1]→[2]→[C]
  ptr2 = meeting pt A →  walks 3 steps: A→B→D→[C]
                                               ↑
                                     both arrive at C simultaneously ✅

KEY INVARIANT: distance(head → cycle_entry) = distance(meeting_point → cycle_entry)
               (measured going forward through the list/cycle).
               One full reset + tandem walk guarantees collision at cycle entry.
```

---

> 🧩 **Drill:**
> Write detectCycleStart() **from memory**. Did you detect first? Reset then move in tandem?

<details>
<summary>Solution</summary>

```java
ListNode slow = head, fast = head;
while (fast != null && fast.next != null) {
    slow = slow.next;
    fast = fast.next.next;
    if (slow == fast) {
        break;
    }
}
if (fast == null || fast.next == null) {
    return null;
}
ListNode ptr1 = head, ptr2 = slow;
while (ptr1 != ptr2) {
    ptr1 = ptr1.next;
    ptr2 = ptr2.next;
}
return ptr1;
```
</details>

---

### Pattern 4 — Pattern Application Gallery

**Problem 4a: LC 142 Linked List Cycle II**

**Problem:** Find node where cycle begins.

**Structure:**
```java
class Solution {
    public ListNode detectCycle(ListNode head) {
        ListNode slow = head, fast = head;
        while (fast != null && fast.next != null) {
            slow = slow.next;
            fast = fast.next.next;
            if (slow == fast) {
                ListNode ptr1 = head, ptr2 = slow;
                while (ptr1 != ptr2) {
                    ptr1 = ptr1.next;
                    ptr2 = ptr2.next;
                }
                return ptr1;
            }
        }
        return null;
    }
}
```

**Time:** O(n), **Space:** O(1)

---

### Pattern 5 — K-Gap Pointer (Kth from End, Remove)

**When you'll see this pattern:**
- LC 19 Remove Nth Node From End — remove without knowing length
- LC 1721 Swapping Nodes in a Linked List — find kth from end
- Real-world example: Finding "k recent items" without counting total

**Problem motivation — concrete example:**

"Given a linked list and an integer n, remove the nth node from the end of the list and return the head."

Example: `1 -> 2 -> 3 -> 4 -> 5`, n=2 → `1 -> 2 -> 3 -> 5` (removed 4)

**Naive approach (and why it fails):**

```java
// Brute: traverse to count length, then traverse again to find position
// Time: O(2n) = O(n), but two passes
// Better: k-gap pointer, one pass
```

**Why this pattern solves it:**

Create a gap of k nodes between two pointers. Advance both until the right pointer reaches the end. The left pointer is now k steps from the end. **Key insight: when right reaches `null`, left is positioned right before the target.**

**Steps in plain English:**

1. Create two pointers: `left` and `right`, initially both at dummy head.
2. Gap creation: Advance `right` by k+1 steps (creates a gap so left points to node BEFORE the one to remove).
3. Advance both: Move both until `right` reaches the end.
4. Remove: `left.next = left.next.next`.

```java
// K-gap pointer pattern (remove nth from end)
ListNode dummy = new ListNode(0, head);
ListNode left = dummy;
ListNode right = dummy;

// Step 2: create gap of n+1
for (int i = 0; i <= n && right != null; i++) {
    right = right.next;
}

// If n is larger than list length
if (right == null) {
    return dummy.next;
}

// Step 3: advance both until right reaches end
while (right != null) {
    left = left.next;
    right = right.next;
}

// Step 4: remove the nth node
left.next = left.next.next;

return dummy.next;
```

**Why this works:** The gap ensures that when `right` reaches `null`, `left` is positioned right before the target node.

**🎨 Visual — K-Gap: Why n+1 (not n) is the Right Gap**

```
List: dummy ──► [1] ──► [2] ──► [3] ──► [4] ──► [5] ──► null
                                                          ↑
Goal: remove the 2nd node from end = node [4]            null

WRONG gap = n (n=2 here):
  Initial: right=dummy, left=dummy
  Advance right 2 steps:    right → [1] → [2]
  Now advance both until right=null:
    step 1: left→[1], right→[3]
    step 2: left→[2], right→[4]
    step 3: left→[3], right→[5]
    step 4: left→[4], right→null  ← loop stops
  left=[4]  but we need left=[3] to do left.next=left.next.next!
  ✗ left is ON the target, not BEFORE it.

RIGHT gap = n+1 (n+1=3 here):
  Initial: right=dummy, left=dummy
  Advance right 3 steps:    right → [1] → [2] → [3]
  Now advance both until right=null:
    step 1: left→[1], right→[4]
    step 2: left→[2], right→[5]
    step 3: left→[3], right→null  ← loop stops
  left=[3]  ← one node BEFORE [4] ✅
  left.next = left.next.next  →  [3].next = [5]
  dummy ──► [1] ──► [2] ──► [3] ──► [5] ──► null  ✅

KEY INVARIANT: gap of n+1 (achieved by starting right from dummy,
               not from head) means when right=null, left is exactly
               one node before the target — enabling the skip with
               left.next = left.next.next.
```

---

> 🧩 **Drill:**
> Write removeNthFromEnd() **from memory**. Did you use dummy? Create gap of n+1 (not n)? Check right != null?

<details>
<summary>Solution</summary>

```java
ListNode dummy = new ListNode(0, head);
ListNode left = dummy, right = dummy;
for (int i = 0; i <= n && right != null; i++) {
    right = right.next;
}
if (right == null) {
    return dummy.next;
}
while (right != null) {
    left = left.next;
    right = right.next;
}
left.next = left.next.next;
return dummy.next;
```
</details>

---

### Pattern 5 — Pattern Application Gallery

**Problem 5a: LC 19 Remove Nth Node From End**

**Problem:** Remove the nth node from end.

**Structure:**
```java
class Solution {
    public ListNode removeNthFromEnd(ListNode head, int n) {
        ListNode dummy = new ListNode(0, head);
        ListNode left = dummy, right = dummy;
        for (int i = 0; i <= n && right != null; i++) {
            right = right.next;
        }
        if (right == null) {
            return dummy.next;
        }
        while (right != null) {
            left = left.next;
            right = right.next;
        }
        left.next = left.next.next;
        return dummy.next;
    }
}
```

**Time:** O(n), **Space:** O(1)

---

**Problem 5b: LC 1721 Swapping Nodes in a Linked List**

**Problem:** Find the kth node from start and kth node from end, swap their values.

**The insight:** Two k-gap pointers, one from start, one from end.

**Structure:**
```java
class Solution {
    public ListNode swapNodes(ListNode head, int k) {
        ListNode first = null, second = null;
        ListNode curr = head;
        for (int i = 1; curr != null; i++) {
            if (i == k) {
                first = curr;
            }
            curr = curr.next;
        }
        int count = 0;
        curr = head;
        while (curr != null) {
            count++;
            curr = curr.next;
        }
        second = head;
        for (int i = 1; i < count - k + 1; i++) {
            second = second.next;
        }
        int temp = first.val;
        first.val = second.val;
        second.val = temp;
        return head;
    }
}
```

**Time:** O(n), **Space:** O(1)

---

## 🔬 Worked Walkthroughs

### WW-1 — LC 206 Reverse Linked List

**Problem statement:** Reverse a singly linked list in-place and return the new head.

**Brute force:** Collect all node values into an array, then overwrite node values in reverse order — O(n) time, O(n) space.

**Intuition bridge:** Three variables carry everything: `prev` (the growing reversed list), `curr` (current node being processed), and `next` (saved before the link is broken). Each iteration flips one arrow and advances both pointers.

**Steps in plain English:**

1. **`prev = null`, `curr = head`**.
2. **While `curr ≠ null`:** save `next = curr.next`; flip `curr.next = prev`; advance `prev = curr`; advance `curr = next`.
3. **Return `prev`** — it is the new head when `curr` reaches null.

```java
class Solution {
    public ListNode reverseList(ListNode head) {
        ListNode prev = null;
        ListNode curr = head;
        while (curr != null) {
            // Step 2 — save next before breaking the link
            ListNode next = curr.next;
            // Step 2 — flip the arrow
            curr.next = prev;
            // Step 2 — advance both pointers
            prev = curr;
            curr = next;
        }
        // Step 3 — prev is the new head
        return prev;
    }
}
```

**Complexity:** Time O(n), Space O(1).

**Transfers to:**

| What's identical | ONE thing different | Key line that changes |
| --- | --- | --- |
| LC 92 Reverse Linked List II | Reverse only positions left..right — insert-at-front for bounded iterations | `for (int i = 0; i < right - left; i++) { pull tail.next to just after conn; }` |
| LC 25 Reverse Nodes in k-Group | Reverse in chunks of k; leave remaining nodes if < k | Outer loop advances by k; inner reversal identical to LC 206 |
| LC 234 Palindrome Linked List | Reverse second half in-place, then compare with first half | Steps: find mid → reverse second half → compare → (restore optional) |

---

### WW-2 — LC 141 Linked List Cycle

**Problem statement:** Return `true` if the linked list contains a cycle.

**Brute force:** Use a `HashSet<ListNode>` of visited nodes — O(n) time, O(n) extra space.

**Intuition bridge:** Floyd's tortoise and hare: slow moves 1 step, fast moves 2 steps. If there is a cycle, fast laps slow inside it and they must collide. If there is no cycle, fast reaches `null` first.

**Steps in plain English:**

1. **`slow = head`, `fast = head`**.
2. **While `fast ≠ null` and `fast.next ≠ null`:** advance `slow` by 1; advance `fast` by 2; if `slow == fast` return `true`.
3. **Return `false`** — fast fell off the end, no cycle.

```java
class Solution {
    public boolean hasCycle(ListNode head) {
        ListNode slow = head;
        ListNode fast = head;
        while (fast != null && fast.next != null) {
            // Step 2 — tortoise moves 1, hare moves 2
            slow = slow.next;
            fast = fast.next.next;
            // Step 2 — collision means cycle exists
            if (slow == fast) {
                return true;
            }
        }
        // Step 3 — fast hit null: no cycle
        return false;
    }
}
```

**Complexity:** Time O(n), Space O(1).

**Transfers to:**

| What's identical | ONE thing different | Key line that changes |
| --- | --- | --- |
| LC 142 Linked List Cycle II | After slow==fast, reset one pointer to head; advance both by 1 until they meet — that meeting point is the cycle entry | `slow = head; while (slow != fast) { slow = slow.next; fast = fast.next; }` |
| LC 876 Middle of Linked List | No cycle — when fast reaches null, slow is at the middle | Remove collision check; just `return slow` when loop ends |
| LC 287 Find Duplicate Number | Array `nums[0..n]` where `nums[i]` is a "next pointer" — cycle entry is the duplicate | Floyd's on array indices: `slow = nums[slow]; fast = nums[nums[fast]];` |

---

### WW-3 — LC 19 Remove Nth Node From End

**Problem statement:** Remove the nth node from the end of the list in a single pass without knowing the length.

**Brute force:** Two passes — first compute length L, then traverse to node at position L − n + 1. O(n) but uses two traversals.

**Intuition bridge:** Maintain a gap of exactly `n + 1` nodes between two pointers. When the right pointer hits `null`, the left pointer's `next` is the node to delete — skip it.

**Steps in plain English:**

1. **Dummy node** pointing to head; `left = dummy`, `right = dummy`.
2. **Advance `right` by `n + 1` steps** (creating the gap).
3. **Advance both** until `right == null`.
4. **`left.next = left.next.next`** — skip the target node.
5. **Return `dummy.next`**.

```java
class Solution {
    public ListNode removeNthFromEnd(ListNode head, int n) {
        // Step 1 — dummy avoids edge case when head itself is deleted
        ListNode dummy = new ListNode(0);
        dummy.next = head;
        ListNode left = dummy;
        ListNode right = dummy;
        // Step 2 — advance right by n+1 to create gap
        for (int i = 0; i <= n; i++) {
            right = right.next;
        }
        // Step 3 — move both until right falls off
        while (right != null) {
            left = left.next;
            right = right.next;
        }
        // Step 4 — left.next is the node to remove
        left.next = left.next.next;
        // Step 5 — dummy.next is the new head
        return dummy.next;
    }
}
```

**Complexity:** Time O(n), Space O(1).

**Transfers to:**

| What's identical | ONE thing different | Key line that changes |
| --- | --- | --- |
| LC 1721 Swapping Nodes in a Linked List | Find kth from start AND kth from end with one pass — same gap trick | Two gap pointers; swap their values (not nodes) at the end |
| LC 61 Rotate List | Rotate right by k — equivalent to finding the new tail (length − k from start) | Find length; reconnect tail to head; advance to position `len − k % len` |
| LC 876 Middle of Linked List | Gap of 0 — slow/fast pointers with no offset; when fast is null, slow is mid | `while (fast != null && fast.next != null) { slow = slow.next; fast = fast.next.next; }` |

---

### WW-4 — LC 21 Merge Two Sorted Lists

**Problem statement:** Merge two sorted linked lists into one sorted linked list and return the head.

**Brute force:** Collect all values from both lists, sort, build a new list — O((m + n) log(m + n)) time, O(m + n) space.

**Intuition bridge:** At each step, the smaller of the two current heads goes next — like merging two sorted decks by always taking from the smaller top card. A dummy head avoids special-casing the first node. After one list is exhausted, append the remaining other directly (already sorted).

**Steps in plain English:**

1. **Dummy head; `curr = dummy`**.
2. **While both `l1` and `l2` are non-null:** attach the node with the smaller value; advance that pointer.
3. **`curr.next = l1 != null ? l1 : l2`** — append the remaining tail.
4. **Return `dummy.next`**.

```java
class Solution {
    public ListNode mergeTwoLists(ListNode l1, ListNode l2) {
        // Step 1 — dummy avoids null-check for first node
        ListNode dummy = new ListNode(0);
        ListNode curr = dummy;
        // Step 2 — pick the smaller head at each step
        while (l1 != null && l2 != null) {
            if (l1.val <= l2.val) {
                curr.next = l1;
                l1 = l1.next;
            } else {
                curr.next = l2;
                l2 = l2.next;
            }
            curr = curr.next;
        }
        // Step 3 — append the non-exhausted tail
        curr.next = (l1 != null) ? l1 : l2;
        // Step 4 — return merged list
        return dummy.next;
    }
}
```

**Complexity:** Time O(m + n), Space O(1).

**Transfers to:**

| What's identical | ONE thing different | Key line that changes |
| --- | --- | --- |
| LC 23 Merge K Sorted Lists | Generalize to k lists — min-heap of (val, node) picks the global minimum each step | `PriorityQueue<ListNode> pq = new PriorityQueue<>(Comparator.comparingInt(n -> n.val));` |
| LC 148 Sort List | This merge is the last step of merge sort on a linked list | `merge(sortList(left), sortList(right))` |
| LC 88 Merge Sorted Array | Same two-pointer merge idea but on arrays (merge in-place from the back) | Merge from right to left to avoid overwriting: `nums1[k--] = ...` |

---

### WW-5 — LC 143 Reorder List

**Problem statement:** Reorder list `[0, 1, 2, ..., n−1]` in-place to `[0, n−1, 1, n−2, ...]` — interleave front and back.

**Brute force:** Collect all nodes in an array; use two pointers (front and back) to relink them — O(n) time, O(n) space.

**Intuition bridge:** Three O(n) O(1)-space phases chain together: (1) find the midpoint to split the list into two halves, (2) reverse the second half, (3) interleave-merge one node from each half at a time.

**Steps in plain English:**

1. **Find mid** with slow/fast pointers; split: `slow.next = null`.
2. **Reverse second half** (LC 206's template).
3. **Interleave:** while both halves have nodes, take one from first, then one from second.

```java
class Solution {
    public void reorderList(ListNode head) {
        if (head == null || head.next == null) {
            return;
        }
        // Step 1 — find mid and split
        ListNode slow = head;
        ListNode fast = head.next;
        while (fast != null && fast.next != null) {
            slow = slow.next;
            fast = fast.next.next;
        }
        ListNode second = slow.next;
        slow.next = null;
        // Step 2 — reverse second half
        ListNode prev = null;
        ListNode curr = second;
        while (curr != null) {
            ListNode next = curr.next;
            curr.next = prev;
            prev = curr;
            curr = next;
        }
        second = prev;
        // Step 3 — interleave first and reversed second half
        ListNode first = head;
        while (second != null) {
            ListNode tmp1 = first.next;
            ListNode tmp2 = second.next;
            first.next = second;
            second.next = tmp1;
            first = tmp1;
            second = tmp2;
        }
    }
}
```

**Complexity:** Time O(n), Space O(1).

**Transfers to:**

| What's identical | ONE thing different | Key line that changes |
| --- | --- | --- |
| LC 234 Palindrome Linked List | Same steps 1 + 2 — compare instead of interleave in step 3 | `while (second != null) { if (first.val != second.val) return false; }` |
| LC 876 Middle of Linked List | Step 1 alone — return the slow pointer after the loop | `return slow;` |
| LC 92 Reverse Linked List II | Step 2 generalized to a bounded sublist — four-pointer reconnect | `for (int i = 0; i < right - left; i++) { ... }` |

---

### WW-6 — LC 92 Reverse Linked List II

**Problem statement:** Reverse the nodes of the list from position `left` to position `right` (1-indexed) in one pass.

**Brute force:** Collect positions `left..right` into an array, reverse values in-place, re-assign to nodes — O(n) time, O(right − left) space.

**Intuition bridge:** Find `conn` (the node just before position `left`) and `tail` (the node at position `left` — it becomes the sublist tail after reversal). Then repeatedly pull `tail.next` to just after `conn` — this is an insert-at-front pattern that reverses the sublist in-place.

**Steps in plain English:**

1. **Dummy → head; `conn = dummy`**. Advance `conn` to position `left − 1`.
2. **`tail = conn.next`** — this node stays at the tail of the reversed sublist.
3. **Repeat `right − left` times:** pull `tail.next` to just after `conn` (insert-at-front).
4. **Return `dummy.next`**.

```java
class Solution {
    public ListNode reverseBetween(ListNode head, int left, int right) {
        // Step 1 — dummy avoids null-check; advance conn to position left-1
        ListNode dummy = new ListNode(0);
        dummy.next = head;
        ListNode conn = dummy;
        for (int i = 0; i < left - 1; i++) {
            conn = conn.next;
        }
        // Step 2 — tail stays at the end of the reversed segment
        ListNode tail = conn.next;
        // Step 3 — insert-at-front: pull tail.next to just after conn
        for (int i = 0; i < right - left; i++) {
            ListNode moved = tail.next;
            tail.next = moved.next;
            moved.next = conn.next;
            conn.next = moved;
        }
        // Step 4 — dummy.next is the new head
        return dummy.next;
    }
}
```

**Complexity:** Time O(n), Space O(1).

**Transfers to:**

| What's identical | ONE thing different | Key line that changes |
| --- | --- | --- |
| LC 206 Reverse Linked List | `left = 1`, `right = n` — full list; no conn setup needed | `prev = null; curr = head; while (curr != null) { ... }` |
| LC 25 Reverse Nodes in k-Group | Outer loop advances in chunks of k; inner reversal identical | `for (int i = 0; i < k - 1; i++) { pull tail.next to just after groupHead; }` |
| LC 61 Rotate List | Equivalent to reversing the last `k` nodes to the front | Find length; rotate = `k % len`; reverse suffix and prefix |

---

### WW-7 — LC 2 Add Two Numbers

**Problem statement:** Two non-empty linked lists store digits of non-negative integers in reverse order. Add them and return the sum as a linked list in reverse order.

**Brute force:** Convert both lists to Java `BigInteger`, add, convert back to a reversed list — works but requires O(n) intermediate strings and doesn't scale to the linked-list interview expectation.

**Intuition bridge:** Digits are already in reverse order — convenient for addition, which also proceeds from least-significant to most-significant. Simulate grade-school addition: at each node, sum both digits plus carry; remainder is the output digit; divide by 10 gives the new carry.

**Steps in plain English:**

1. **Dummy head; `curr = dummy`; `carry = 0`**.
2. **While `l1 ≠ null` or `l2 ≠ null` or `carry ≠ 0`:** `val = (l1 val or 0) + (l2 val or 0) + carry`; `carry = val / 10`; append `new ListNode(val % 10)`; advance `l1`, `l2`, `curr`.
3. **Return `dummy.next`**.

```java
class Solution {
    public ListNode addTwoNumbers(ListNode l1, ListNode l2) {
        // Step 1 — dummy head; carry starts at 0
        ListNode dummy = new ListNode(0);
        ListNode curr = dummy;
        int carry = 0;
        // Step 2 — process until both lists are exhausted and carry is 0
        while (l1 != null || l2 != null || carry != 0) {
            int val = carry;
            if (l1 != null) {
                val += l1.val;
                l1 = l1.next;
            }
            if (l2 != null) {
                val += l2.val;
                l2 = l2.next;
            }
            carry = val / 10;
            curr.next = new ListNode(val % 10);
            curr = curr.next;
        }
        // Step 3 — dummy.next is the result head
        return dummy.next;
    }
}
```

**Complexity:** Time O(max(m, n)), Space O(max(m, n)) for the output list.

**Transfers to:**

| What's identical | ONE thing different | Key line that changes |
| --- | --- | --- |
| LC 445 Add Two Numbers II | Digits in forward order — reverse both lists first, then apply LC 2 | `l1 = reverse(l1); l2 = reverse(l2);` before the main loop |
| LC 415 Add Strings | Same carry logic on digit-character strings (no overflow concerns) | `val = (s1.charAt(i) - '0') + (s2.charAt(j) - '0') + carry;` |
| LC 67 Add Binary | Binary digits (0/1) — carry at 2 instead of 10 | `carry = val / 2; result = val % 2;` |

---

### WW-8 — LC 148 Sort List

**Problem statement:** Sort a linked list in O(n log n) time. O(1) space is a follow-up goal.

**Brute force:** Extract all values into an array, sort with `Arrays.sort`, rebuild the list — O(n log n) time, O(n) space.

**Intuition bridge:** Merge sort maps naturally to linked lists: finding the midpoint splits the list in O(n) with no extra space, and merging two sorted lists is O(n) with O(1) extra space. Recursion costs O(log n) stack space; bottom-up merge sort achieves true O(1).

**Steps in plain English:**

1. **Base case:** 0 or 1 nodes — return head.
2. **Find mid** with slow/fast; split (`mid.next = null`).
3. **Recurse:** `left = sortList(head)`; `right = sortList(mid)`.
4. **Merge** the two sorted halves (LC 21's merge template).
5. **Return** merged head.

```java
class Solution {
    public ListNode sortList(ListNode head) {
        // Step 1 — base case: 0 or 1 nodes already sorted
        if (head == null || head.next == null) {
            return head;
        }
        // Step 2 — find mid and split
        ListNode slow = head;
        ListNode fast = head.next;
        while (fast != null && fast.next != null) {
            slow = slow.next;
            fast = fast.next.next;
        }
        ListNode mid = slow.next;
        slow.next = null;
        // Step 3 — sort each half recursively
        ListNode left = sortList(head);
        ListNode right = sortList(mid);
        // Step 4 — merge two sorted halves
        return merge(left, right);
    }

    private ListNode merge(ListNode l1, ListNode l2) {
        ListNode dummy = new ListNode(0);
        ListNode curr = dummy;
        while (l1 != null && l2 != null) {
            if (l1.val <= l2.val) {
                curr.next = l1;
                l1 = l1.next;
            } else {
                curr.next = l2;
                l2 = l2.next;
            }
            curr = curr.next;
        }
        curr.next = (l1 != null) ? l1 : l2;
        return dummy.next;
    }
}
```

**Complexity:** Time O(n log n), Space O(log n) for recursion stack.

**Transfers to:**

| What's identical | ONE thing different | Key line that changes |
| --- | --- | --- |
| LC 21 Merge Two Sorted Lists | The merge step alone — no recursion needed | Call `mergeTwoLists(l1, l2)` directly |
| LC 23 Merge K Sorted Lists | K-way merge — use a min-heap seeded with all list heads | `PriorityQueue<ListNode> pq = new PriorityQueue<>(k, Comparator.comparingInt(n -> n.val));` |
| LC 912 Sort an Array | Same merge sort skeleton on arrays — O(n) extra space for the temp array | `int[] temp = new int[right - left + 1];` during merge |

## ⚠️ Gotchas — Silent Bug Hall of Fame

### Gotcha 1 — Null Dereference (Most Common)

**The bug:**
```java
// ❌ Wrong — curr could be null
while (curr.next != null) {
    System.out.println(curr.val);
    curr = curr.next;
}

// ✅ Right — check curr first
while (curr != null) {
    System.out.println(curr.val);
    curr = curr.next;
}
```

Without the `curr != null` check, you'll hit an NPE when reaching the end.

---

### Gotcha 2 — Orphaning the Rest of the List

**The bug:**
```java
// ❌ Wrong — lost the next pointer before mutating
ListNode temp = curr.next.val;
curr.next = newNode;
// Can't continue traversal now

// ✅ Right — save the next node pointer, not the value
ListNode next = curr.next;
curr.next = newNode;
// Later, curr = next to continue traversal
```

If you don't save `curr.next` **before** mutating, you orphan the rest of the list. Silent bug: code compiles, runs, but produces wrong output.

---

### Gotcha 3 — Fast Pointer Out of Bounds

**The bug:**
```java
// ❌ Wrong — if fast is null, fast.next will NPE
while (fast.next != null) {
    slow = slow.next;
    fast = fast.next.next;
}

// ✅ Right — check fast and fast.next
while (fast != null && fast.next != null) {
    slow = slow.next;
    fast = fast.next.next;
}
```

Fast moves 2 steps. At boundaries, both `fast` and `fast.next` must be checked.

---

### Gotcha 4 — Infinite Loop in Cycle Detection

**The bug:**
```java
// ❌ Wrong — if no cycle, you'll loop forever or NPE
while (slow != fast) {
    slow = slow.next;
    fast = fast.next.next;
}

// ✅ Right — bound the loop with null checks
while (fast != null && fast.next != null) {
    slow = slow.next;
    fast = fast.next.next;
    if (slow == fast) {
        return true;
    }
}
return false;
```

Always check for `null` termination. Otherwise, if there's no cycle, `fast` will eventually NPE.

---

### Gotcha 5 — Off-by-One in K-Gap Pointer

**The bug:**
```java
// ❌ Wrong — gap is k, but you need left BEFORE the target
for (int i = 0; i < k; i++) {
    right = right.next;
}

// ✅ Right — gap is k+1 to position left before target
for (int i = 0; i <= k; i++) {
    right = right.next;
}
```

When removing the kth node, left must point to the **node before** the target. Gap should be k+1, not k.

---

## 🗺️ Practice Plan (in tiers)

### Tier 1 — Foundational Patterns ⭐ (Must Be Muscle Memory)

Master insertion, reversal, and cycle detection first.

- ✅ LC 206 Reverse Linked List — three-pointer reversal
- ✅ LC 141 Linked List Cycle — slow-fast detection
- ✅ LC 237 Delete Node in a Linked List — deletion by value

### Tier 2 — Finding & Reordering ⭐

Use slow-fast and k-gap pointers to locate and modify.

- ✅ LC 876 Middle of the Linked List — find middle (Pattern 3)
- 🟡 **Try After Tier 1** — LC 142 Linked List Cycle II — find cycle start (Pattern 4)
- 🟡 **Try After Tier 1** — LC 19 Remove Nth Node From End — k-gap pointer (Pattern 5)
- 🟡 **Try After Tier 2** — LC 143 Reorder List — combine slow-fast + reversal (Patterns 2, 3, 5)

### Tier 3 — Multi-Pass & Merging ⭐

Combine multiple passes to build the answer.

- 🟡 **Try After Tier 2** — LC 21 Merge Two Sorted Lists — two pointers, no reversal
- 🟡 **Try After Tier 2** — LC 160 Intersection of Two Linked Lists — finding common nodes
- 🔴 **Reference Only** — LC 2 Add Two Numbers — treat nodes as digits, add with carry

### Tier 4 — Advanced (Multi-Pattern / Complex Logic)

Patterns requiring intuition from earlier tiers.

- 🔴 **Reference Only** — LC 25 Reverse Nodes in K-Group — reverse sublists (Pattern 2 + k-gap)
- 🔴 **Reference Only** — LC 24 Swap Nodes in Pairs — swap adjacent nodes (careful pointer handling)
- 🔴 **Reference Only** — LC 138 Copy List with Random Pointer — deep copy with hash map

---

## 🧾 TL;DR — One-Page Summary

**Mental Model:**
- Linked List = linear structure where each node holds a value and a next pointer.
- Pointers are like hands in a relay race. Always know: where you came from, where you are, where you're going.

**Universal Skeleton:**
```java
ListNode curr = head;
while (curr != null) {
    // Do something with curr
    curr = curr.next;
}
```

**5 Core Patterns:**
1. **Dummy-Head Insertion** — eliminates head special case, uniform insertion logic
2. **Three-Pointer Reversal** — save next before mutation, O(1) space
3. **Slow-Fast Pointers** — find middle in one pass, detect cycle (Floyd's algorithm)
4. **Cycle-Start Detection** — two-pass convergence after initial cycle detection
5. **K-Gap Pointer** — find kth from end without knowing length, one pass

**4 Universal Habits:**
1. Always null-check before dereferencing
2. Save next pointer before mutation
3. Use dummy for insertion (handles head case)
4. Write printList helper for testing

**3 Gotchas:**
1. Null dereference (check before accessing)
2. Orphaned list (save next before mutation)
3. Fast pointer out of bounds (check `fast && fast.next`)

**Tier 1 You Must Master:**
- LC 206 Reverse, LC 141 Cycle, LC 237 Delete Node

**Complexity:**
- All 5 patterns: O(n) time, O(1) space

---

## 🔗 Cross-References

- **Pointer arithmetic details:** See `DSA/Reference/code-style-for-dsa-reference.md`
- **Cycle math proof:** See `DSA/DeepDive/graphs-fundamentals.md` (Floyd's algorithm)
- **Similar pointer patterns:** See `DSA/DeepDive/two-pointers-sliding-window-fundamentals.md`
- **Interview playbook:** See `DSA/Interview/linked-list.md` (problem taxonomy)

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| June 2026 | **Deep dive rewritten with Pattern Application Gallery.** Now covers 5 core patterns (Dummy-Head Insertion, Three-Pointer Reversal, Slow-Fast Pointer, Cycle-Start Detection, K-Gap Pointer) with problem motivation structure (problem → naive approach → why pattern solves it → steps → code → drill). Pattern Application Gallery with 3 most-asked problems per pattern. 3 worked walkthroughs (LC 206, LC 141, LC 19). 5 gotchas with ❌/✅ code examples. 4-tier practice plan. Curriculum alignment (Striver, LeetCode editorials, GeeksforGeeks). |
