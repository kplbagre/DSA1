# Linked List — Interview Playbook

> **Read this file when:** You have an interview in 1-2 days and need to quickly map linked list problems to the right pattern. Linked lists have only 5 core patterns — master them and you cover 90% of interview questions.

---

## 🎯 Why You're Reading This

Linked list problems look scary because pointer manipulation is error-prone. But they're actually the most **pattern-predictable** topic in DSA — almost every problem maps to one of 5 templates. This file gives you the pattern recognition cues and the exact pointer operations.

---

## 🔧 Essential Methods — Know These Cold

| Method / Idiom | What it does | Used in |
| --- | --- | --- |
| `node.next` | Access next node in the chain | All patterns |
| `node.val` | Access node's value | All patterns |
| `new ListNode(val)` | Create a new node (for dummy head, etc.) | Patterns 3, 5 |
| `ListNode dummy = new ListNode(0)` | Dummy head — simplifies edge cases | Patterns 3, 5 |
| `slow = slow.next` / `fast = fast.next.next` | Floyd's two-speed traversal | Pattern 1 |
| `ListNode temp = curr.next` | Save next before overwriting (reversal) | Pattern 2 |
| `prev = curr; curr = temp` | The three-pointer reversal dance | Pattern 2 |
| `new PriorityQueue<>((a, b) -> Integer.compare(a.val, b.val))` | Min-heap of ListNodes by value (see fallback below) | Pattern 3 (Merge K) |

> **Note:** Linked list problems are about pointer manipulation, not Java API methods. The "methods" here are field accesses and constructor calls — no `import` needed.

### 🔄 Lambda & Shorthand Explanations with Fallbacks

**`new PriorityQueue<>((a, b) -> Integer.compare(a.val, b.val))` — Min-heap of ListNodes**

```java
// What it does:
//   Creates a min-heap that orders ListNodes by their .val field
//   (a, b) -> Integer.compare(a.val, b.val) is a Comparator lambda:
//     Returns NEGATIVE → a comes first (a.val < b.val)
//     Returns POSITIVE → b comes first (b.val < a.val)
//     Returns ZERO     → equal priority
//   Integer.compare is overflow-safe (unlike a.val - b.val which can overflow)
PriorityQueue<ListNode> pq = new PriorityQueue<>((a, b) -> Integer.compare(a.val, b.val));

// 🔄 Fallback — if comparator lambdas confuse you, use a.val - b.val
//   (safe here because node values are bounded ints, won't overflow):
PriorityQueue<ListNode> pq = new PriorityQueue<>((a, b) -> a.val - b.val);

// 🔄 Fallback 2 — avoid PriorityQueue entirely, merge pairs iteratively:
//   Merge lists[0] with lists[1], then result with lists[2], etc. O(N*K) but simple.
```

---

## 🧠 The Mental Model — Every Linked List Problem Is Pointer Surgery

```
Linked list problem
│
├── "Detect something about structure"
│   ├── Cycle?              → Floyd's Slow/Fast (Pattern 1)
│   ├── Middle node?        → Slow/Fast (Pattern 1 variant)
│   └── Intersection point? → Two-pointer alignment (Pattern 1 variant)
│
├── "Reverse part or all"
│   ├── Reverse entire list → Iterative reversal (Pattern 2)
│   └── Reverse sublist     → Reverse between positions (Pattern 2 variant)
│
├── "Merge / combine lists"
│   ├── Merge 2 sorted      → Two-pointer merge (Pattern 3)
│   └── Merge K sorted      → Heap + merge (Pattern 3 variant)
│
├── "Remove / reorder nodes"
│   ├── Remove Nth from end → Slow/Fast with gap (Pattern 4)
│   └── Reorder list        → Split + reverse + merge (Pattern 4 variant)
│
└── "Don't know where to start"
    └── Use a DUMMY NODE (Pattern 5) → simplifies head-might-change cases
```

**The dummy node trick:** Whenever the head might change (merge, remove, partition), create `ListNode dummy = new ListNode(0); dummy.next = head;` and return `dummy.next`. This eliminates 90% of null-pointer edge cases.

### 🎨 Visual — Pointer Surgery Principle

```
BEFORE (remove node B):
    A ──→ B ──→ C ──→ D

    Step: A.next = B.next

AFTER:
    A ──────→ C ──→ D
         B (orphaned, garbage collected)

RULE: To remove a node, change the PREVIOUS node's .next pointer.
      You can never "go back" — so you need to be standing at
      the node BEFORE the one you want to remove.
```

**KEY INVARIANT:** In a singly linked list, you can only modify what's AHEAD of your current pointer. To delete node X, you must be at X's predecessor.

---

## 🧭 Pattern 1: Floyd's Slow/Fast Pointer ⭐

**Recognition cues — reach for this when:**
- "Detect cycle in linked list"
- "Find the start of the cycle"
- "Find the middle node"
- "Determine if a linked list is a palindrome" (find middle → reverse second half → compare)

**The core idea:** `slow` moves 1 step, `fast` moves 2 steps. If there's a cycle, they'll meet. If not, `fast` reaches the end.

### Cycle Detection (LC 141):

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

### Find Middle (LC 876):

When `fast` reaches the end, `slow` is at the middle.

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

**Odd vs Even length:** For even-length lists, this returns the **second** middle node (e.g., for `[1,2,3,4]`, returns node 3). If you need the first middle, use `while (fast.next != null && fast.next.next != null)`.

### Find Cycle Start (LC 142):

After slow and fast meet, move one pointer to head. Then advance both at speed 1. They meet at the cycle start.

```java
public ListNode detectCycle(ListNode head) {
    ListNode slow = head;
    ListNode fast = head;

    while (fast != null && fast.next != null) {
        slow = slow.next;
        fast = fast.next.next;
        if (slow == fast) {
            // Phase 2: find cycle start
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

**🏷️ Problems:** LC 141 (Has Cycle), LC 142 (Cycle Start), LC 876 (Middle of Linked List), LC 234 (Palindrome Linked List — middle + reverse + compare).

---

## 🧭 Pattern 2: Reverse a Linked List ⭐

**Recognition cues — reach for this when:**
- "Reverse a linked list"
- "Reverse between position left and right"
- Part of a larger problem (palindrome check, reorder list, add two numbers reversed)

**The three-pointer dance:** `prev`, `curr`, `next`. At each step: save next, reverse the link, advance.

### 🎨 Visual — Reverse Step by Step

```
Initial:   null ← prev    curr → next → ...
                            │
Step 1: Save next           │     next = curr.next
Step 2: Reverse link        │     curr.next = prev
Step 3: Advance prev        │     prev = curr
Step 4: Advance curr        │     curr = next

Iteration 1:
   null    1 → 2 → 3 → null
   prev  curr

   null ← 1    2 → 3 → null
         prev  curr

Iteration 2:
   null ← 1 ← 2    3 → null
               prev  curr

Iteration 3:
   null ← 1 ← 2 ← 3    null
                   prev  curr (null → stop)

Result: prev = 3 → 2 → 1 → null
```

**KEY INVARIANT:** After processing node `curr`, everything before `prev` (inclusive) is reversed. `curr` always points to the first unreversed node.

**Steps in plain English:**

1. **Initialize** — `prev = null`, `curr = head`.
2. **Loop** — while `curr` is not null: save `curr.next`, reverse the link, advance both.
3. **Return `prev`** — it's the new head.

```java
public ListNode reverseList(ListNode head) {
    // Step 1 — initialize
    ListNode prev = null;
    ListNode curr = head;

    // Step 2 — three-pointer dance
    while (curr != null) {
        ListNode next = curr.next;
        curr.next = prev;
        prev = curr;
        curr = next;
    }

    // Step 3 — prev is the new head
    return prev;
}
```

### Reverse Between Positions (LC 92):

```java
public ListNode reverseBetween(ListNode head, int left, int right) {
    ListNode dummy = new ListNode(0);
    dummy.next = head;

    // Step 1 — walk to the node BEFORE position 'left'
    ListNode beforeLeft = dummy;
    for (int i = 1; i < left; i++) {
        beforeLeft = beforeLeft.next;
    }

    // Step 2 — reverse 'right - left' links
    ListNode prev = null;
    ListNode curr = beforeLeft.next;
    for (int i = 0; i <= right - left; i++) {
        ListNode next = curr.next;
        curr.next = prev;
        prev = curr;
        curr = next;
    }

    // Step 3 — reconnect
    beforeLeft.next.next = curr;
    beforeLeft.next = prev;

    return dummy.next;
}
```

**🏷️ Problems:** LC 206 (Reverse Linked List), LC 92 (Reverse Linked List II), LC 25 (Reverse Nodes in k-Group — advanced).

---

## 🧭 Pattern 3: Merge Two Sorted Lists ⭐

**Recognition cues — reach for this when:**
- "Merge two sorted linked lists"
- "Merge K sorted lists" (use heap)
- "Sort a linked list" (merge sort — split at middle, sort halves, merge)

**Steps in plain English:**

1. **Dummy node** — `ListNode dummy = new ListNode(0)`.
2. **Compare heads** — take the smaller one, advance that pointer.
3. **Attach remaining** — when one list is exhausted, attach the other.

```java
public ListNode mergeTwoLists(ListNode l1, ListNode l2) {
    // Step 1 — dummy node
    ListNode dummy = new ListNode(0);
    ListNode tail = dummy;

    // Step 2 — compare and attach
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

    // Step 3 — attach remaining
    tail.next = (l1 != null) ? l1 : l2;

    return dummy.next;
}
```

**🏷️ Problems:** LC 21 (Merge Two Sorted Lists), LC 23 (Merge K Sorted Lists — heap), LC 148 (Sort List — merge sort).

---

## 🧭 Pattern 4: Remove / Reorder with Gap Pointer

**Recognition cues — reach for this when:**
- "Remove Nth node from end"
- "Reorder list" (L0 → Ln → L1 → Ln-1 → ...)

### Remove Nth from End (LC 19):

**The gap trick:** Advance `fast` by N steps first. Then move both `slow` and `fast` together. When `fast` reaches the end, `slow` is at the node BEFORE the one to remove.

```java
public ListNode removeNthFromEnd(ListNode head, int n) {
    ListNode dummy = new ListNode(0);
    dummy.next = head;

    ListNode fast = dummy;
    ListNode slow = dummy;

    // Advance fast by n + 1 steps (so slow lands BEFORE the target)
    for (int i = 0; i <= n; i++) {
        fast = fast.next;
    }

    // Move together until fast reaches end
    while (fast != null) {
        slow = slow.next;
        fast = fast.next;
    }

    // Remove the node
    slow.next = slow.next.next;

    return dummy.next;
}
```

### Reorder List (LC 143):

This is a **three-pattern combo**: find middle (Pattern 1) → reverse second half (Pattern 2) → interleave merge (Pattern 3).

```java
public void reorderList(ListNode head) {
    if (head == null || head.next == null) {
        return;
    }

    // Step 1 — find middle (slow/fast)
    ListNode slow = head;
    ListNode fast = head;
    while (fast.next != null && fast.next.next != null) {
        slow = slow.next;
        fast = fast.next.next;
    }

    // Step 2 — reverse second half
    ListNode secondHalf = slow.next;
    slow.next = null;
    ListNode prev = null;
    ListNode curr = secondHalf;
    while (curr != null) {
        ListNode next = curr.next;
        curr.next = prev;
        prev = curr;
        curr = next;
    }
    secondHalf = prev;

    // Step 3 — interleave merge
    ListNode first = head;
    ListNode second = secondHalf;
    while (second != null) {
        ListNode tmp1 = first.next;
        ListNode tmp2 = second.next;
        first.next = second;
        second.next = tmp1;
        first = tmp1;
        second = tmp2;
    }
}
```

**🏷️ Problems:** LC 19 (Remove Nth From End), LC 143 (Reorder List), LC 234 (Palindrome Linked List).

---

## 🧭 Pattern 5: Dummy Node — The Universal Safety Net

**Recognition cues — reach for this when:**
- The head of the list might change (remove head, merge, partition)
- You're not sure if you need it (just use it — costs nothing)

**The rule:** `ListNode dummy = new ListNode(0); dummy.next = head;` at the start. Return `dummy.next` at the end. This eliminates special-case handling for operations that might remove or replace the head.

```java
// Without dummy — painful special cases
public ListNode removeElements(ListNode head, int val) {
    while (head != null && head.val == val) {
        head = head.next;        // special case for head!
    }
    ListNode curr = head;
    while (curr != null && curr.next != null) {
        if (curr.next.val == val) {
            curr.next = curr.next.next;
        } else {
            curr = curr.next;
        }
    }
    return head;
}

// With dummy — clean and uniform
public ListNode removeElements(ListNode head, int val) {
    ListNode dummy = new ListNode(0);
    dummy.next = head;
    ListNode curr = dummy;
    while (curr.next != null) {
        if (curr.next.val == val) {
            curr.next = curr.next.next;
        } else {
            curr = curr.next;
        }
    }
    return dummy.next;
}
```

**🏷️ Problems:** Every merge/remove/partition problem benefits. LC 203 (Remove Elements), LC 82 (Remove Duplicates II), LC 86 (Partition List).

---

## 🔬 Canonical Problem — LC 206: Reverse Linked List

> **Problem:** Given the head of a singly linked list, reverse the list, and return the reversed list.

### Step 1 — Read and identify triggers

"The problem says **reverse**. This is **Pattern 2: Iterative Reversal**. Three pointers: `prev`, `curr`, `next`."

### Step 2 — Visualize the pointer dance

```
Start:    null    1 → 2 → 3 → null
          prev   curr

After 1:  null ← 1    2 → 3 → null
               prev  curr

After 2:  null ← 1 ← 2    3 → null
                     prev  curr

After 3:  null ← 1 ← 2 ← 3    null
                          prev  curr (null, stop)

Return prev (= node 3, which points back to 2 → 1 → null)
```

### Step 3 — Code

```java
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
```

### Step 4 — Verify

Input: `1 → 2 → 3 → null`

| Step | prev | curr | next | After link reversal |
| --- | --- | --- | --- | --- |
| 1 | null | 1 | 2 | null ← 1 |
| 2 | 1 | 2 | 3 | null ← 1 ← 2 |
| 3 | 2 | 3 | null | null ← 1 ← 2 ← 3 |
| End | 3 | null | — | Return 3 |

### Complexity

- **Time:** O(n) — single pass
- **Space:** O(1) — three pointers only

---

## ⚡ Problem Bank — Expanded

---

### LC 206: Reverse Linked List

> **Problem:** Reverse a singly linked list. `1→2→3→4→5` → `5→4→3→2→1`.

> **Approach:** Three-pointer dance: `prev`, `curr`, `next`. At each step: save next, reverse link, advance.

```java
// Save next before we overwrite it
ListNode next = curr.next;
// Reverse the link — point current node backward
curr.next = prev;
// Advance prev and curr one step forward
prev = curr;
curr = next;
```

---

### LC 21: Merge Two Sorted Lists

> **Problem:** Merge two sorted linked lists into one sorted list. `1→2→4` + `1→3→4` → `1→1→2→3→4→4`.

> **Approach:** Dummy node + compare heads. Take the smaller head each time.

```java
ListNode dummy = new ListNode(0), tail = dummy;
while (l1 != null && l2 != null) {
    // Take the smaller head and advance that list's pointer
    if (l1.val <= l2.val) {
        tail.next = l1;
        l1 = l1.next;
    } else {
        tail.next = l2;
        l2 = l2.next;
    }
    tail = tail.next;
}
// Attach whichever list still has remaining nodes
tail.next = (l1 != null) ? l1 : l2;
```

---

### LC 141: Linked List Cycle

> **Problem:** Return true if the linked list has a cycle (some node's next points to a previous node).

> **Approach:** Floyd's slow/fast. Slow moves 1 step, fast moves 2. If they meet → cycle. If fast reaches null → no cycle.

```java
while (fast != null && fast.next != null) {
    slow = slow.next;
    fast = fast.next.next;
    // If they meet, there must be a cycle (fast lapped slow)
    if (slow == fast) return true;
}
```

---

### LC 142: Linked List Cycle II

> **Problem:** Return the node where the cycle begins. Return null if no cycle.

> **Approach:** Floyd's Phase 1 (detect meeting point) + Phase 2 (reset one to head, both move at speed 1 — they meet at cycle start).

```java
if (slow == fast) {
    // Phase 2 — reset one pointer to head, both advance at speed 1
    ListNode entry = head;
    // They meet at the cycle entry point (Floyd's mathematical proof)
    while (entry != slow) {
        entry = entry.next;
        slow = slow.next;
    }
    return entry;
}
```

---

### LC 19: Remove Nth Node From End of List

> **Problem:** Remove the Nth node from the end. `1→2→3→4→5, n=2` → `1→2→3→5` (removed 4).

> **Approach:** Gap pointer. Advance fast by N+1 steps, then move both until fast reaches null. Slow is before the target.

```java
// Advance fast by N+1 so slow lands one node BEFORE the target
for (int i = 0; i <= n; i++) fast = fast.next;
// Move both until fast hits null — slow is now the predecessor
while (fast != null) {
    slow = slow.next;
    fast = fast.next;
}
// Skip over the Nth-from-end node
slow.next = slow.next.next;
```

---

### LC 143: Reorder List

> **Problem:** Reorder `L0→L1→…→Ln-1→Ln` to `L0→Ln→L1→Ln-1→L2→Ln-2→…`. In-place.

> **Approach:** Three-pattern combo: (1) find middle with slow/fast, (2) reverse second half, (3) interleave merge both halves.

```java
// 1. Find middle: slow/fast
// 2. Reverse: slow.next = null; reverse second half
// 3. Interleave: alternate nodes from first and reversed second
```

---

### LC 876: Middle of the Linked List

> **Problem:** Return the middle node. For even-length, return the second middle. `[1,2,3,4,5]` → node 3.

> **Approach:** Slow/fast. When fast reaches end, slow is at middle.

```java
// When fast reaches end, slow is at middle (fast travels 2x speed)
while (fast != null && fast.next != null) {
    slow = slow.next;
    fast = fast.next.next;
}
return slow;
```

---

### LC 234: Palindrome Linked List

> **Problem:** Check if a linked list is a palindrome. `1→2→2→1` → true.

> **Approach:** Find middle → reverse second half → compare both halves node by node.

```java
// Split at middle, reverse the second half
ListNode mid = findMiddle(head);
ListNode rev = reverse(mid);
// Compare first half (head) with reversed second half node by node
while (rev != null) {
    if (head.val != rev.val) return false;
    head = head.next;
    rev = rev.next;
}
```

---

### LC 2: Add Two Numbers

> **Problem:** Two non-negative integers stored as reversed linked lists. Add them and return sum as a linked list. `2→4→3` + `5→6→4` = `7→0→8` (342 + 465 = 807).

> **Approach:** Walk both lists with carry. Create new nodes for each digit.

```java
// Treat exhausted lists as 0 — handles unequal lengths
int sum = (l1 != null ? l1.val : 0) + (l2 != null ? l2.val : 0) + carry;
// Integer division gives the carry for the next digit
carry = sum / 10;
// Modulo gives the current digit to store
curr.next = new ListNode(sum % 10);
```

---

### LC 23: Merge K Sorted Lists

> **Problem:** Merge `k` sorted linked lists into one sorted list. Lists: `[1→4→5, 1→3→4, 2→6]` → `1→1→2→3→4→4→5→6`.

> **Approach:** Min-heap of size K. Poll smallest head, add its next to heap. O(N log K) total.

```java
// (a, b) -> Integer.compare(a.val, b.val): min-heap ordering by node value
// Integer.compare is overflow-safe; returns negative if a.val < b.val
PriorityQueue<ListNode> pq = new PriorityQueue<>((a, b) -> Integer.compare(a.val, b.val));
// 🔄 Fallback: new PriorityQueue<>((a, b) -> a.val - b.val)  (safe for bounded ints)
// Add all non-null heads, then poll-and-advance
```

---

### LC 92: Reverse Linked List II

> **Problem:** Reverse nodes from position `left` to `right` (1-indexed). Example: `1→2→3→4→5, left=2, right=4` → `1→4→3→2→5`.

> **Approach:** Use dummy node. Walk to node before `left`. Then reverse `right - left` times using the "insert-at-front" technique — repeatedly move the next node to after the `prev` node.

```java
ListNode dummy = new ListNode(0, head);
ListNode prev = dummy;
// Walk prev to the node just before position 'left'
for (int i = 1; i < left; i++) prev = prev.next;
ListNode curr = prev.next;
// Insert-at-front reversal: repeatedly move curr's next to after prev
for (int i = 0; i < right - left; i++) {
    ListNode temp = curr.next;
    // Detach temp from the chain
    curr.next = temp.next;
    // Insert temp right after prev (at the front of the reversed section)
    temp.next = prev.next;
    prev.next = temp;
}
```

---

### LC 25: Reverse Nodes in k-Group

> **Problem:** Reverse every `k` consecutive nodes. If remaining nodes < k, leave as-is. Example: `1→2→3→4→5, k=2` → `2→1→4→3→5`.

> **Approach:** Count k nodes ahead. If enough, reverse that group (reuse LC 206 reversal). Connect the reversed group's tail to the next group's result (recursion or iteration).

```java
// Count k nodes ahead to check if a full group exists
ListNode curr = head;
int count = 0;
while (curr != null && count < k) {
    curr = curr.next;
    count++;
}
if (count == k) {
    // Full group exists — reverse it and recurse on the rest
    ListNode reversed = reverse(head, k);
    // After reversal, 'head' is now the tail of this group — connect to next group
    head.next = reverseKGroup(curr, k);
    return reversed;
}
// Fewer than k nodes remain — leave as-is
return head;
```

---

### LC 148: Sort List

> **Problem:** Sort a linked list in O(n log n) time and O(1) space. Example: `4→2→1→3` → `1→2→3→4`.

> **Approach:** Merge sort on linked list. Find middle (slow/fast), split, sort each half recursively, merge (LC 21 merge two sorted).

```java
// Find middle with slow/fast
ListNode mid = getMid(head);
ListNode left = sortList(head);
ListNode right = sortList(mid);
return merge(left, right);
```

---

### LC 203: Remove Linked List Elements

> **Problem:** Remove all nodes with value `val`. Example: `1→2→6→3→4→5→6, val=6` → `1→2→3→4→5`.

> **Approach:** Dummy node + single pass. If `curr.next.val == val`, skip it (`curr.next = curr.next.next`). Otherwise advance.

```java
ListNode dummy = new ListNode(0, head);
ListNode curr = dummy;
while (curr.next != null) {
    // Skip the node by pointing over it, or advance if value doesn't match
    if (curr.next.val == val) curr.next = curr.next.next;
    else curr = curr.next;
}
return dummy.next;
```

---

### LC 82: Remove Duplicates from Sorted List II

> **Problem:** Remove ALL nodes that have duplicate values from a **sorted** list. Example: `1→2→3→3→4→4→5` → `1→2→5`.

> **Approach:** Dummy node. When `curr.next.val == curr.next.next.val`, record the duplicate value and skip ALL nodes with that value.

```java
ListNode dummy = new ListNode(0, head);
ListNode prev = dummy;
while (prev.next != null && prev.next.next != null) {
    if (prev.next.val == prev.next.next.val) {
        // Found duplicates — record the value and skip ALL nodes with it
        int dup = prev.next.val;
        while (prev.next != null && prev.next.val == dup) prev.next = prev.next.next;
    } else {
        prev = prev.next;
    }
}
```

---

### LC 86: Partition List

> **Problem:** Partition list around value `x` — all nodes < x come before nodes ≥ x. Preserve original order within each partition. Example: `1→4→3→2→5→2, x=3` → `1→2→2→4→3→5`.

> **Approach:** Two dummy lists: `less` and `greater`. Walk original, append each node to the appropriate list. Connect `less.tail → greater.head`.

```java
// Two separate chains: one for nodes < x, one for nodes >= x
ListNode lessHead = new ListNode(0), greaterHead = new ListNode(0);
ListNode less = lessHead, greater = greaterHead;
while (head != null) {
    // Route each node to the appropriate chain
    if (head.val < x) {
        less.next = head;
        less = less.next;
    } else {
        greater.next = head;
        greater = greater.next;
    }
    head = head.next;
}
// Terminate the greater chain to avoid a cycle
greater.next = null;
// Connect less chain's tail to greater chain's head
less.next = greaterHead.next;
return lessHead.next;
```

---

## ⚠️ Interview Gotchas

### Edge cases interviewers will probe:

- **Empty list** (`head == null`) — return null, not NPE
- **Single node** — reverse = itself, middle = itself
- **Even vs odd length** — middle node definition varies (ask the interviewer!)
- **Removing the head** — dummy node prevents special cases

### Follow-up questions:

| After solving... | They'll ask... | Answer |
| --- | --- | --- |
| Reverse (iterative) | "Can you do it recursively?" | Yes: base case `head.next == null`, recursive call, then `head.next.next = head; head.next = null` |
| Has Cycle | "Where does the cycle start?" | Floyd's Phase 2: reset one pointer to head, advance both at speed 1 |
| Merge Two | "Merge K sorted lists?" | Min-heap of size K: O(N log K) total |
| Palindrome Check | "Without modifying the list?" | Use a stack for the first half, then compare |

### The #1 linked list bug:

**Forgetting to null-terminate after splitting.** When you split a list (e.g., for reorder or sort), you MUST set `slow.next = null` to break the connection. Otherwise you have infinite loops or corrupted structure.

```java
// ❌ forgot to break
ListNode secondHalf = slow.next;
// 'slow' still points to secondHalf — list is not actually split!

// ✅ break the connection
ListNode secondHalf = slow.next;
slow.next = null;
```

---

## 🧩 Speed Drill — 7 Minutes

**Part 1 — Pattern Recognition (2 minutes)**

1. "Detect if linked list has a cycle" → ___
2. "Reverse a linked list" → ___
3. "Merge two sorted linked lists" → ___
4. "Remove Nth node from end" → ___
5. "Find middle of linked list" → ___
6. "Reorder list L0→Ln→L1→Ln-1" → ___

**Answers:** 1. Floyd's Slow/Fast, 2. Three-pointer reversal, 3. Dummy + compare, 4. Gap pointer (fast leads by N+1), 5. Slow/Fast (fast reaches end), 6. Middle + Reverse + Interleave

**Part 2 — Write the Template (3 minutes)**

From memory, write the iterative `reverseList` method. Four lines in the while loop. No peeking.

**Part 3 — The Null-Terminate Trap (2 minutes)**

You're splitting a list at the middle for merge sort. Write the 3 lines that: (1) find the middle, (2) save the second half, (3) break the connection.

**Answer:**
```java
ListNode secondHalf = slow.next;
slow.next = null;
// Now 'head' is first half, 'secondHalf' is second half
```

**Scoring:** All 3 parts correct = ready. Missed the null-terminate = re-read the gotchas section.

---

## 🔗 Cross-References

| Topic | File |
| --- | --- |
| Linked list reference (method syntax) | `DSA/Reference/linkedlist-reference.md` |
| Floyd's slow/fast extended | `DSA/DeepDive/two-pointers-sliding-window-fundamentals.md` |
| Java coding traps (null checks, == vs .equals) | `DSA/Implementation/java-coding-traps.md` |

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| May 2026 | **File created.** Interview Playbook for Linked Lists. 5 patterns: Floyd's, reversal, merge, gap pointer, dummy node. Canonical walkthrough (LC 206), 10-problem bank, null-terminate trap warning. |
| May 2026 | **Lambda/fallback pass.** Added PriorityQueue comparator to Essential Methods. Added 🔄 Lambda section. Inline comment + `🔄 Fallback` at LC 23 PriorityQueue comparator usage. |
