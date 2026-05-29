# Linked List — Reference

> Compact daily-revision file for linked list patterns. Every template is notepad-ready — write the imports block first, then pick the pattern.

---

## ⚡ Imports — Write These First on a Blank Notepad

```java
// Most LC linked list problems provide ListNode — you don't import it.
// But on a plain notepad, write the class definition first:

class ListNode {
    int val;
    ListNode next;
    ListNode() {}
    ListNode(int val) { this.val = val; }
    ListNode(int val, ListNode next) { this.val = val; this.next = next; }
}
```

> **On LeetCode** `ListNode` is pre-defined. On a **plain notepad / Google Doc**, write the class above before anything else. No `java.util.*` imports needed for basic linked list problems. For problems needing a `Map` or `Set`, add:

```java
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
```

---

## 🎯 The Mental Model (10 Seconds)

**A linked list is a chain of nodes where each node points to the next.** You can't random-access — you must walk. Every linked list trick comes from **pointer manipulation**: reassigning `.next` to rewire the chain.

**Three questions for any linked list problem:**
1. Do I need a **dummy head** (to avoid null-checking the head on every operation)?
2. Am I doing a **two-pass** (find length first, then act) or **one-pass** (fast/slow pointers)?
3. Am I **mutating in-place** (rewire `.next`) or **building a new list**?

---

## 🧭 The 10 Core Patterns

---

### 1. Dummy Head (Sentinel Node) ⭐

> Create a fake node before the real head. Return `dummy.next` at the end. Eliminates all "if head is null" and "if we're removing the head" edge cases.

```java
public ListNode solve(ListNode head) {
    ListNode dummy = new ListNode(0, head);
    ListNode curr = dummy;
    // ... manipulate curr.next ...
    return dummy.next;
}
```

> **Use a dummy whenever the head itself might change** — remove operations, merge, partition, any problem where the first node isn't guaranteed to stay.

**🏷️ Used in:** LC 203 (Remove Elements), LC 21 (Merge Two Lists), LC 2 (Add Two Numbers), LC 86 (Partition List), LC 25 (Reverse Nodes in k-Group).

---

### 2. Reverse a Linked List ⭐

> Three pointers: `prev`, `curr`, `next`. On each step: save next, point curr backward, advance both.

**Steps in plain English:**

1. **Initialize** — `prev = null`, `curr = head`.
2. **Loop** while `curr != null`: save `curr.next` in a temp, point `curr.next` to `prev`, advance `prev` to `curr`, advance `curr` to the saved next.
3. **Return** `prev` — it's now the new head.

```java
public ListNode reverseList(ListNode head) {
    ListNode prev = null;
    ListNode curr = head;
    while (curr != null) {
        // Step 1: save next before we overwrite it
        ListNode next = curr.next;
        // Step 2: reverse the pointer
        curr.next = prev;
        // Step 3: advance both pointers
        prev = curr;
        curr = next;
    }
    return prev;
}
```

**Recursive version** (less common in interviews, but good to know):

```java
public ListNode reverseList(ListNode head) {
    if (head == null || head.next == null) {
        return head;
    }
    ListNode newHead = reverseList(head.next);
    head.next.next = head;
    head.next = null;
    return newHead;
}
```

**🏷️ Example problems:** LC 206 (Reverse Linked List), LC 92 (Reverse II — partial), LC 25 (Reverse in k-Group), LC 234 (Palindrome — reverse second half).

---

### 3. Reverse a Sub-section (Between Positions) 🟡

> Reverse nodes from position `left` to `right` (1-indexed). Walk to the node before `left`, then reverse `right - left + 1` nodes, then stitch.

```java
public ListNode reverseBetween(ListNode head, int left, int right) {
    ListNode dummy = new ListNode(0, head);
    ListNode prev = dummy;

    // Step 1: walk prev to the node BEFORE position left
    for (int i = 1; i < left; i++) {
        prev = prev.next;
    }

    // Step 2: reverse (right - left + 1) nodes starting from prev.next
    ListNode curr = prev.next;
    for (int i = 0; i < right - left; i++) {
        ListNode next = curr.next;
        curr.next = next.next;
        next.next = prev.next;
        prev.next = next;
    }

    return dummy.next;
}
```

**🏷️ Example:** LC 92 (Reverse Linked List II).

---

### 4. Fast & Slow (Tortoise and Hare) ⭐

> Two pointers: `slow` moves 1 step, `fast` moves 2 steps. When `fast` reaches the end, `slow` is at the middle. If there's a cycle, they'll meet inside it.

#### 4a. Find the middle node

```java
public ListNode middleNode(ListNode head) {
    ListNode slow = head;
    ListNode fast = head;
    while (fast != null && fast.next != null) {
        slow = slow.next;
        fast = fast.next.next;
    }
    return slow;
}
```

> For even-length lists, this returns the **second** middle. To get the first middle, use `while (fast.next != null && fast.next.next != null)`.

**🏷️ Example:** LC 876 (Middle of Linked List).

#### 4b. Detect a cycle

```java
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
```

**🏷️ Example:** LC 141 (Linked List Cycle).

#### 4c. Find cycle start (Floyd's algorithm — full version)

```java
public ListNode detectCycle(ListNode head) {
    ListNode slow = head;
    ListNode fast = head;

    // Phase 1: detect cycle (meet inside the loop)
    while (fast != null && fast.next != null) {
        slow = slow.next;
        fast = fast.next.next;
        if (slow == fast) {
            // Phase 2: find the entry point
            // Reset one pointer to head, advance both at speed 1
            ListNode entry = head;
            while (entry != slow) {
                entry = entry.next;
                slow = slow.next;
            }
            return entry;
        }
    }
    return null;
}
```

> **Why Phase 2 works:** When slow and fast meet, slow has traveled `d + k` steps (d = distance to cycle start, k = distance into the cycle). Resetting one pointer to head and advancing both at speed 1 means they'll meet at the cycle start after exactly `d` more steps. This is a math proof — memorize the algorithm, not the proof.

**🏷️ Example:** LC 142 (Linked List Cycle II), LC 287 (Find Duplicate Number — treat array as linked list).

---

### 5. Merge Two Sorted Lists ⭐

> Dummy head + compare-and-advance pattern.

```java
public ListNode mergeTwoLists(ListNode l1, ListNode l2) {
    ListNode dummy = new ListNode(0);
    ListNode tail = dummy;

    while (l1 != null && l2 != null) {
        if (l1.val <= l2.val) {
            tail.next = l1;
            l1 = l1.next;
        } else {
            tail.next = l2;
            l2 = l2.next;
        }
        tail = tail.next;
    }

    // Attach whichever list has remaining nodes
    tail.next = (l1 != null) ? l1 : l2;
    return dummy.next;
}
```

**🏷️ Example problems:** LC 21 (Merge Two Sorted Lists), LC 23 (Merge K Sorted Lists — use a min-heap of heads).

---

### 6. Remove the N-th Node from End (Two-Pointer Gap)

> Advance `fast` by `n` steps. Then advance both until `fast` reaches the end. `slow` is now one node before the target.

```java
public ListNode removeNthFromEnd(ListNode head, int n) {
    ListNode dummy = new ListNode(0, head);
    ListNode fast = dummy;
    ListNode slow = dummy;

    // Step 1: advance fast by (n + 1) steps so there's a gap of n between them
    for (int i = 0; i <= n; i++) {
        fast = fast.next;
    }

    // Step 2: advance both until fast hits null
    while (fast != null) {
        slow = slow.next;
        fast = fast.next;
    }

    // Step 3: slow.next is the target — skip it
    slow.next = slow.next.next;
    return dummy.next;
}
```

**🏷️ Example:** LC 19 (Remove Nth Node From End of List).

---

### 7. Palindrome Check (Reverse Second Half In-Place)

> Find middle → reverse second half → compare → (optionally restore).

```java
public boolean isPalindrome(ListNode head) {
    // Step 1: find middle (slow ends up at start of second half)
    ListNode slow = head;
    ListNode fast = head;
    while (fast != null && fast.next != null) {
        slow = slow.next;
        fast = fast.next.next;
    }

    // Step 2: reverse second half
    ListNode prev = null;
    ListNode curr = slow;
    while (curr != null) {
        ListNode next = curr.next;
        curr.next = prev;
        prev = curr;
        curr = next;
    }

    // Step 3: compare first half and reversed second half
    ListNode left = head;
    ListNode right = prev;
    while (right != null) {
        if (left.val != right.val) {
            return false;
        }
        left = left.next;
        right = right.next;
    }
    return true;
}
```

**🏷️ Example:** LC 234 (Palindrome Linked List).

---

### 8. Add Two Numbers (Digit-by-Digit with Carry)

> Walk both lists simultaneously, sum digits + carry, build result node-by-node.

```java
public ListNode addTwoNumbers(ListNode l1, ListNode l2) {
    ListNode dummy = new ListNode(0);
    ListNode curr = dummy;
    int carry = 0;

    while (l1 != null || l2 != null || carry != 0) {
        int sum = carry;
        if (l1 != null) {
            sum += l1.val;
            l1 = l1.next;
        }
        if (l2 != null) {
            sum += l2.val;
            l2 = l2.next;
        }
        carry = sum / 10;
        curr.next = new ListNode(sum % 10);
        curr = curr.next;
    }

    return dummy.next;
}
```

**🏷️ Example problems:** LC 2 (Add Two Numbers), LC 445 (Add Two Numbers II — reverse inputs or use stacks).

---

### 9. Intersection of Two Lists

> Calculate length difference. Advance the longer list's pointer by the difference. Then walk both together — they'll meet at the intersection (or both reach null).

```java
public ListNode getIntersectionNode(ListNode headA, ListNode headB) {
    // Trick: two-pass with swap.
    // When pointer A reaches the end, redirect to headB (and vice versa).
    // Both traverse exactly lenA + lenB nodes, so they align at the intersection.
    ListNode a = headA;
    ListNode b = headB;
    while (a != b) {
        a = (a != null) ? a.next : headB;
        b = (b != null) ? b.next : headA;
    }
    return a;
}
```

> **Why this works:** After the swap, both pointers have traveled `lenA + lenB - commonTail` steps. If no intersection, both reach `null` simultaneously and the loop ends.

**🏷️ Example:** LC 160 (Intersection of Two Linked Lists).

---

### 10. Sort a Linked List (Merge Sort) 🟡

> Split in half via slow/fast → recursively sort both halves → merge. O(n log n) time, O(log n) stack.

```java
public ListNode sortList(ListNode head) {
    // Base case: empty or single node
    if (head == null || head.next == null) {
        return head;
    }

    // Step 1: find middle and split
    ListNode slow = head;
    ListNode fast = head.next;
    while (fast != null && fast.next != null) {
        slow = slow.next;
        fast = fast.next.next;
    }
    ListNode secondHalf = slow.next;
    slow.next = null;

    // Step 2: recursively sort both halves
    ListNode left = sortList(head);
    ListNode right = sortList(secondHalf);

    // Step 3: merge (reuse pattern 5)
    return mergeTwoLists(left, right);
}
```

**🏷️ Example:** LC 148 (Sort List).

---

## ⚡ Pattern-Picker Decision Tree

```
Linked List problem
│
├── "Reverse the list"                    → Pattern 2 (3-pointer iterative)
├── "Reverse from position L to R"        → Pattern 3 (walk + partial reverse)
├── "Is there a cycle?"                   → Pattern 4b (fast/slow)
├── "Find cycle start"                    → Pattern 4c (Floyd's full)
├── "Find middle"                         → Pattern 4a (fast/slow)
├── "Merge two sorted lists"              → Pattern 5 (dummy + compare)
├── "Remove N-th from end"               → Pattern 6 (gap technique)
├── "Is it a palindrome?"                → Pattern 7 (middle + reverse + compare)
├── "Add digit-by-digit"                  → Pattern 8 (carry loop)
├── "Two lists intersect?"                → Pattern 9 (swap-at-end trick)
├── "Sort a linked list"                  → Pattern 10 (merge sort)
└── "Head might change"                   → Start with Pattern 1 (dummy head)
```

---

## ⚠️ Gotchas (Silent Bug Hall of Fame)

---

**Forgetting to use a dummy head when the head might be removed.**

```java
// ❌ head could be removed — need special case
if (head.val == target) { head = head.next; }
// But what if the new head also matches? Need a loop. Ugly.

// ✅ dummy eliminates the entire class of bugs
ListNode dummy = new ListNode(0, head);
```

---

**Not saving `curr.next` before overwriting it during reverse.**

```java
// ❌ curr.next is gone after this line
curr.next = prev;
ListNode next = curr.next;    // this is now prev, not the original next!

// ✅ save FIRST, then overwrite
ListNode next = curr.next;
curr.next = prev;
```

---

**Off-by-one in fast/slow — even vs odd length.**

```java
// For "middle" problems:
// fast starts at head     → returns SECOND middle for even-length
// fast starts at head.next → returns FIRST middle for even-length
// Know which one the problem needs. LC 876 wants second middle.
```

---

**Null pointer on `fast.next.next` — always check `fast.next` first.**

```java
// ❌ NPE when fast.next is null
while (fast != null) {
    fast = fast.next.next;
}

// ✅ check both
while (fast != null && fast.next != null) {
    fast = fast.next.next;
}
```

---

**Forgetting `carry != 0` in the Add Two Numbers loop condition.**

```java
// ❌ Misses the final carry (e.g., 5 + 5 = 10, carry=1 needs an extra node)
while (l1 != null || l2 != null) { ... }

// ✅ Include carry in the condition
while (l1 != null || l2 != null || carry != 0) { ... }
```

---

**Not cutting the link when splitting for merge sort.**

```java
// ❌ forgot slow.next = null — the list isn't actually split
ListNode secondHalf = slow.next;
// Both halves still connected — infinite recursion / wrong answers

// ✅ cut the link
ListNode secondHalf = slow.next;
slow.next = null;
```

---

**Using `==` to compare node values instead of `.val`.**

```java
// ❌ Compares object references, not values
if (node1 == node2) { ... }

// ✅ Compare values
if (node1.val == node2.val) { ... }

// Exception: LC 160 (Intersection) genuinely compares references
// because we want the same node object, not just same value
```

---

**Losing the head reference.**

```java
// ❌ After traversal, head points to the last node
while (head.next != null) {
    head = head.next;
}
// Can't return the original head anymore

// ✅ Use a separate pointer
ListNode curr = head;
while (curr.next != null) {
    curr = curr.next;
}
return head;    // still the original
```

---

## ⚡ Quick Cheat Sheet

| If you need... | Use... | Key detail |
| --- | --- | --- |
| Reverse entire list | 3-pointer (prev/curr/next) | Return `prev` |
| Reverse sub-section L..R | Walk to L-1, partial reverse | Use dummy head |
| Find middle | Fast/slow pointers | `fast != null && fast.next != null` |
| Detect cycle | Fast/slow | They meet inside the cycle |
| Find cycle entry | Floyd's Phase 1 + Phase 2 | Reset one to head, walk at speed 1 |
| Merge two sorted | Dummy + compare + tail.next | Attach remainder at end |
| Remove N-th from end | Fast leads by N+1 steps | Dummy avoids head-removal edge case |
| Palindrome check | Middle → reverse 2nd half → compare | O(1) space |
| Add two numbers | Carry loop + dummy | `carry != 0` in loop condition |
| Two lists intersection | Swap-at-end two-pointer | Both travel lenA + lenB |
| Sort linked list | Merge sort (split + recurse + merge) | Cut the link with `slow.next = null` |

---

## 🗺️ Practice Plan — At-a-Glance

| Tier | Goal | Problems |
| --- | --- | --- |
| **1 — Essentials** ✅ | Write from blank notepad | LC 206 (Reverse), LC 21 (Merge Two), LC 141 (Cycle) |
| **2 — Core Medium** 🟡 | Solve in < 15 min | LC 19 (Remove Nth), LC 2 (Add Two Numbers), LC 142 (Cycle II), LC 876 (Middle) |
| **3 — Combination** 🟡 | Pattern combos | LC 234 (Palindrome), LC 148 (Sort List), LC 92 (Reverse II), LC 160 (Intersection) |
| 🎯 **STOP — Medium-Interview Cutoff** 🎯 | | |
| **4 — Hard** 🔴 | Optional | LC 23 (Merge K Lists), LC 25 (Reverse k-Group), LC 138 (Copy List with Random Pointer) |

---

## 🔗 Cross-References

| Concept | See File |
| --- | --- |
| Floyd's cycle → array duplicate detection | `DSA/Reference/arrays-reference.md` — Pattern 11 (Cyclic Sort) |
| Merge pattern reused in merge sort | `DSA/DeepDive/recursion-fundamentals.md` |
| HashMap for linked list problems (LRU Cache) | `DSA/Reference/hashmap-section-updated.md` |

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| May 2026 | **Initial version.** 10 core patterns covering reverse, fast/slow, merge, cycle, palindrome, add-two-numbers, intersection, sort. Notepad-ready with ListNode definition + imports at top. 8 gotchas from real practice bugs. |
