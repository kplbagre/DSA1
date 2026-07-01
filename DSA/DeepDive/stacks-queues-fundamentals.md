# Stacks, Queues & Priority Queues — Deep Dive

> **What you'll learn:** How stacks, queues, and priority queues work under the hood, when to use each, how to solve stack-based patterns (bracket matching, monotonic stacks, min-stack), queue-based patterns (BFS, sliding window), and the gotchas unique to these data structures.

> **Audience:** You know HashMap. Now learn stack/queue mechanics: when ArrayDeque beats LinkedList, when PriorityQueue wins, and which patterns they unlock.

---

> **Curriculum alignment:** This deep-dive synthesizes:
> - **Striver's Stack & Queue Series** (6+ videos covering ArrayDeque, PriorityQueue, monotonic stack, sliding window maximum)
> - **LeetCode Problem Editorials** (LC 20 Valid Parentheses, LC 155 Min Stack, LC 239 Sliding Window Maximum, LC 84 Largest Rectangle, LC 739 Daily Temperatures)
> - **GeeksforGeeks foundational concepts** (stack as ADT, queue as ADT, heap structure, deque mechanics)
>
> **Credit:** ArrayDeque deep-dive, monotonic stack patterns, min-stack design from Striver. Problem-driven examples from LeetCode editorials. Pattern Application Gallery (most-asked interview problems per pattern) and FAANG interview context are this doc's contribution.

---

## 🎯 Why You're Reading This

After reading this, you will:

1. **Own Stack & Queue cold** — know when ArrayDeque (not LinkedList), when PriorityQueue, performance trade-offs
2. **Master monotonic patterns** — next greater element, daily temperatures, largest rectangle (these three solve ~15% of FAANG medium/hard)
3. **Recognize 5 core stack patterns** — bracket matching, monotonic stack, min-stack, largest rectangle, longest valid parentheses
4. **Recognize 4 core queue patterns** — level-order BFS, sliding window maximum, task scheduling, LRU cache setup
5. **Know when PriorityQueue wins** — Kth largest, merge K sorted lists, frequency-based sorting, task scheduler
6. **Avoid 3 silent bugs** — stack underflow, LinkedList as queue performance, PriorityQueue iteration order

---

## 🚦 Difficulty Tagging — Read Before You Pick a Problem

| Tag | Meaning | Action |
| --- | --- | --- |
| ✅ **Try Now** | Solvable with concepts covered up to this point | Open LeetCode, attempt cold, time-box ~25 min |
| 🟡 **Try After [Section X]** | Needs concepts from a later section | Bookmark, return after the named section |
| 🔴 **Reference Only** | Needs concepts beyond this doc | Read editorial for awareness; don't attempt cold |

---

## 🌲 Definition — What Is a Stack? What Is a Queue?

**Stack (LIFO — Last In, First Out):**
- Last element added is first element removed.
- Use case: undo/redo, call stack, bracket matching, DFS, expression evaluation.

**Queue (FIFO — First In, First Out):**
- First element added is first element removed.
- Use case: BFS, level-order traversal, task scheduling, print queue.

**Priority Queue (Heap-backed):**
- Remove in order of priority, not insertion order.
- Use case: Kth largest, merge K sorted lists, task scheduler, event simulation.

**Simplest examples:**

```java
// Stack
Deque<Integer> stack = new ArrayDeque<>();
stack.push(1);  // add to top
stack.push(2);
System.out.println(stack.pop());  // 2 (last added, first removed)

// Queue
Queue<Integer> queue = new ArrayDeque<>();
queue.offer(1);  // add to tail
queue.offer(2);
System.out.println(queue.poll());  // 1 (first added, first removed)

// Priority Queue (min-heap)
PriorityQueue<Integer> pq = new PriorityQueue<>();
pq.offer(5);
pq.offer(2);
System.out.println(pq.poll());  // 2 (smallest, not first added)
```

---

## 📖 Terminology Table

| Term | Meaning | Interview context |
| --- | --- | --- |
| **LIFO / FILO** | Last In, First Out / First In, Last Out | Stack property |
| **FIFO / LILO** | First In, First Out / Last In, Last Out | Queue property |
| **Monotonic stack** | Stack where elements are in increasing (or decreasing) order | "Next greater element", "largest rectangle", "daily temperatures" |
| **Monotonic deque** | Deque maintaining sorted (monotonic) order for sliding window max | "Max in each window" using deque instead of heap |
| **Sliding window** | Fixed-size or variable-size window on an array/stream | "Max in window", "subarray with constraint k" |
| **Deque** | Double-ended queue (both ends support add/remove) | ArrayDeque implements Deque; used for both stack and queue |
| **Heap** | Complete binary tree where parent ≤ children (min-heap) or ≥ (max-heap) | PriorityQueue backing structure |
| **Underflow** | Pop from empty stack or poll from empty queue | Common bug; check isEmpty first |

---

## 🧠 Mental Model — Stack Is "Things I Haven't Dealt With Yet"

**Stack intuition:**
- You're in a diner. Orders arrive on a stack (pile of papers). You take the top order (last one received) first.
- Why? It's usually the most urgent, or it's easiest to grab from the top.
- **Real-world:** browser undo (undo the most recent action first), call stack (return from innermost function first), DFS (recursion is implicit stack).

**Queue intuition:**
- You're at a checkout. Customers queue (line). First customer in line is served first.
- Why? Fair and predictable.
- **Real-world:** print queue (first job sent prints first), BFS (level-by-level exploration), task queue (execute jobs in arrival order).

**Priority Queue intuition:**
- You're a hospital triage. Patients don't enter and exit in order. Critical patients jump the queue.
- You maintain a min-heap: always grab the patient with highest priority (lowest priority number).
- **Real-world:** operating system scheduling (high-priority tasks run first), Kth largest (keep a min-heap of top K), event simulation.

---

## 🎨 Visual — Stack vs Queue vs PriorityQueue vs Monotonic Deque

```
Stack (LIFO):
        push 1, push 2, push 3
        
        [1]        ← base
        [2]
        [3]        ← top (pop here)
        
        pop → 3, pop → 2, pop → 1

Queue (FIFO):
        offer 1, offer 2, offer 3
        
        [1]        ← head (poll here)
        [2]
        [3]        ← tail (offer here)
        
        poll → 1, poll → 2, poll → 3

PriorityQueue (min-heap):
        offer 5, offer 2, offer 8, offer 1
        
        Internally:        1
                         /   \
                        2     8
                       /
                      5
        
        poll → 1, poll → 2, poll → 5, poll → 8

Monotonic Deque (sliding window max):
        nums = [1, 3, 1, 2, 0, 5], k = 3
        Deque stores INDICES in decreasing order of values
        
        i=0: [0]          (1)
        i=1: [1]          (3 > 1, pop 0)
        i=2: [1, 2]       (1 < 3, keep 1)
        i=3: [3]          (2 < 3, but 2 ≥ 1, pop 2, keep 1 outside window)
        
        Window maxes: [3, 3, 2, 5]

KEY INVARIANT:
- Stack: LIFO, O(1) push/pop, useful for ordering problems
- Queue: FIFO, O(1) offer/poll, useful for level-order traversal
- PriorityQueue: O(log n) offer/poll, useful for priority-based retrieval
- Monotonic Deque: O(1) amortized per element, useful for sliding window max
```

---

## 🎨 Style Habits — Build These From Day 1

### 🌐 Universal Habits

#### Habit 1 — Use ArrayDeque, never java.util.Stack

**Why:** `java.util.Stack` is legacy, synchronized (slow), unnecessary overhead.

❌ **Bad:**
```java
Stack<Integer> stack = new Stack<>();
stack.push(5);
```

✅ **Good:**
```java
Deque<Integer> stack = new ArrayDeque<>();
stack.push(5);  // push = addFirst
stack.pop();    // pop = removeFirst
stack.peek();   // peek = getFirst
```

---

#### Habit 2 — Use ArrayDeque for Queue, not LinkedList

**Why:** ArrayDeque is backed by a circular array (no allocation overhead). LinkedList allocates a node per element.

❌ **Bad:**
```java
Queue<Integer> queue = new LinkedList<>();
queue.offer(1);
```

✅ **Good:**
```java
Queue<Integer> queue = new ArrayDeque<>();
queue.offer(1);   // offer = addLast
queue.poll();     // poll = removeFirst
queue.peek();     // peek = getFirst
```

---

#### Habit 3 — Check isEmpty before pop/poll

**Why:** Prevents underflow (exception when popping empty stack).

❌ **Bad:**
```java
Deque<Integer> stack = new ArrayDeque<>();
stack.pop();  // throws NoSuchElementException if empty
```

✅ **Good:**
```java
Deque<Integer> stack = new ArrayDeque<>();
if (!stack.isEmpty()) {
    int val = stack.pop();
}

// Or use peek + check for null
Integer val = stack.peek();
if (val != null) {
    stack.pop();
}
```

---

#### Habit 4 — PriorityQueue: offer/poll, not add/remove

**Why:** `.offer()` and `.poll()` return null on failure (empty); `.add()` and `.remove()` throw exceptions.

✅ **Good:**
```java
PriorityQueue<Integer> pq = new PriorityQueue<>();
pq.offer(5);  // always succeeds in Java (unbounded)
Integer val = pq.poll();  // null if empty, otherwise next min
```

---

### 🔧 Context-Specific Habits

#### Habit 5 — Monotonic stack: maintain sorted order as you iterate

**When:** "Next greater element", "largest rectangle", "daily temperatures" — problems where you compare current to previous elements.

**Pattern:**
```java
// Next Greater Element: for each element, find the next larger element to its right
Deque<Integer> stack = new ArrayDeque<>();
int[] result = new int[nums.length];

for (int i = nums.length - 1; i >= 0; i--) {
    // Pop all smaller elements (they're useless now)
    while (!stack.isEmpty() && stack.peek() <= nums[i]) {
        stack.pop();
    }
    
    // Top of stack is the next greater (or empty = no next greater)
    result[i] = stack.isEmpty() ? -1 : stack.peek();
    
    // Push current for future comparisons
    stack.push(nums[i]);
}
```

---

#### Habit 6 — Min stack: dual-stack pattern (value + min)

**When:** You need to track the minimum while supporting push/pop.

**Pattern:**
```java
class MinStack {
    Deque<Integer> stack = new ArrayDeque<>();
    Deque<Integer> minStack = new ArrayDeque<>();  // parallel stack tracking min at each depth
    
    public void push(int val) {
        stack.push(val);
        minStack.push(Math.min(minStack.isEmpty() ? val : minStack.peek(), val));
    }
    
    public void pop() {
        stack.pop();
        minStack.pop();
    }
    
    public int getMin() {
        return minStack.peek();  // O(1)
    }
}
```

---

#### Habit 7 — Monotonic deque for sliding window max

**When:** "Max in each window", "min in each window" — fixed-size window problems.

**Pattern:**
```java
// Store INDICES (not values) in deque, in DECREASING order of values
Deque<Integer> deque = new ArrayDeque<>();
int[] result = new int[nums.length - k + 1];

for (int i = 0; i < nums.length; i++) {
    // Remove indices outside window
    while (!deque.isEmpty() && deque.peekFirst() < i - k + 1) {
        deque.removeFirst();
    }
    
    // Remove indices with smaller values (they can't be max in future windows)
    while (!deque.isEmpty() && nums[deque.peekLast()] <= nums[i]) {
        deque.removeLast();
    }
    
    // Add current index
    deque.addLast(i);
    
    // Window max is at front of deque
    if (i >= k - 1) {
        result[i - k + 1] = nums[deque.peekFirst()];
    }
}
```

---

> **Quick recap:** Use ArrayDeque, not Stack. Use ArrayDeque, not LinkedList for Queue. Check isEmpty before pop/poll. PriorityQueue offer/poll. Monotonic stack maintains sorted order. Min stack uses dual stacks. Monotonic deque for sliding window max.

---

## 🔨 Choosing the Right Variant — Before Writing the Loop

> **The Phase 1 question for stacks and queues:** *Which variant do I reach for?* The wrong choice forces you to rewrite the whole loop. Match keywords in the problem to the table below before writing a single line.

| Problem keywords | Variant | Java declaration |
| --- | --- | --- |
| Bracket matching, DFS traversal, expression evaluation, undo/redo | **Stack** (LIFO) | `Deque<T> stack = new ArrayDeque<>()` |
| BFS, level-order, task scheduling, sliding window (fixed) | **Queue** (FIFO) | `Queue<T> queue = new ArrayDeque<>()` |
| Next greater element, daily temperatures, largest rectangle, trapped water | **Monotonic Stack** (indices, not values) | `Deque<Integer> stack = new ArrayDeque<>()` |
| Max (or min) in each sliding window of size K | **Monotonic Deque** (indices, evict from both ends) | `Deque<Integer> deque = new ArrayDeque<>()` |
| Kth largest / smallest, merge K sorted lists, heap-based greedy | **PriorityQueue** | `PriorityQueue<T> pq = new PriorityQueue<>()` (min); `Collections.reverseOrder()` for max |
| getMin() / getMax() in O(1) alongside push/pop | **Dual Stack** | Two `Deque` instances kept in sync |

```
Before writing the loop, answer:
  □ Does order matter LIFO or FIFO?
      LIFO → Stack (ArrayDeque).   FIFO → Queue (ArrayDeque).
  □ Do I need "next greater / smaller" or "largest rectangle"?
      → Monotonic Stack — store indices, not values.
  □ Do I need sliding window max / min?
      → Monotonic Deque — evict from front (out of window) and back (dominated).
  □ Do I need Kth or sorted order on the fly?
      → PriorityQueue.
```

---

## 🧭 Patterns — Stack, Queue, Priority Queue

### Pattern 1 — Bracket Matching (Classic Stack)

**When you'll see this pattern:**
- LC 20 Valid Parentheses — check if brackets are balanced
- LC 1249 Minimum Remove to Make Valid Parentheses — remove invalid brackets to make valid
- LC 1614 Maximum Nesting Depth of the Parentheses — track nesting depth
- Real-world example: Compiler syntax checking (match braces, parens, brackets)

**Problem motivation — concrete example:**

"Given a string containing brackets `(`, `)`, `{`, `}`, `[`, `]`, determine if the input string is valid. Opening brackets must be closed by the same type in the correct order."

Example: `"({[]})"` → `true`; `"({[}])"` → `false`

**Naive approach (and why it fails):**

```java
// Brute force: for each closing bracket, scan backwards for matching opening
// Time: O(n²) — for each closing bracket, scan left part
// Space: O(1)
// Problem: On LC 20 (n=10k) → 100M operations → TLE
```

**Why this pattern solves it:**

A stack keeps track of opening brackets as you scan forward. When you encounter a closing bracket, the stack top should match. No need to rescan backwards. **Key insight: the most recent unclosed bracket must be the one that matches the current closing bracket.**

**Steps in plain English:**

1. Create a stack.
2. Iterate through each character.
3. If opening bracket, push to stack.
4. If closing bracket, check if stack top matches. If yes, pop. If no, invalid.
5. At end, stack must be empty (all brackets matched).

```java
// Bracket matching pattern
Map<Character, Character> pairs = Map.of(')', '(', '}', '{', ']', '[');
Deque<Character> stack = new ArrayDeque<>();

for (char c : s.toCharArray()) {
    // Step 3-4: closing bracket
    if (pairs.containsKey(c)) {
        if (stack.isEmpty() || stack.peek() != pairs.get(c)) {
            return false;  // mismatch
        }
        stack.pop();
    } else {
        // Step 3: opening bracket
        stack.push(c);
    }
}

// Step 5: all matched?
return stack.isEmpty();
```

**Why this works:** Stack ensures LIFO matching — the most recent unclosed bracket is always at the top, so it's the correct candidate for matching.

---

> 🧩 **Drill — do this NOW before reading further:**
> Write code to check if parentheses are balanced (ignoring other characters).

<details>
<summary>Solution</summary>

```java
Deque<Character> stack = new ArrayDeque<>();
for (char c : s.toCharArray()) {
    if (c == '(' || c == '{' || c == '[') {
        stack.push(c);
    } else if (c == ')' || c == '}' || c == ']') {
        if (stack.isEmpty()) {
            return false;
        }
        char open = stack.pop();
        if (!matches(open, c)) {
            return false;
        }
    }
}
return stack.isEmpty();
```
</details>

---

### Pattern 1 — Pattern Application Gallery

**Problem 1a: LC 20 Valid Parentheses**

**Problem:** Check if brackets are balanced and correctly nested.

**Naive approach:**
```java
// Brute: for each closing bracket, search backwards for opening
// Time: O(n²), Space: O(1)
for (int i = 0; i < s.length(); i++) {
    if (isClosing(s.charAt(i))) {
        boolean found = false;
        for (int j = i - 1; j >= 0; j--) {
            if (matches(s.charAt(j), s.charAt(i))) {
                found = true;
                break;
            }
        }
        if (!found) {
            return false;
        }
    }
}
```

**The insight:** Stack tracks most recent unclosed bracket. No rescanning.

**Structure:**
```java
class Solution {
    public boolean isValid(String s) {
        Map<Character, Character> map = Map.of(')', '(', '}', '{', ']', '[');
        Deque<Character> stack = new ArrayDeque<>();
        for (char c : s.toCharArray()) {
            if (map.containsKey(c)) {
                if (stack.isEmpty() || stack.peek() != map.get(c)) {
                    return false;
                }
                stack.pop();
            } else {
                stack.push(c);
            }
        }
        return stack.isEmpty();
    }
}
```

**Time:** O(n), **Space:** O(n)

---

**Problem 1b: LC 1249 Minimum Remove to Make Valid Parentheses**

**Problem:** Remove minimum characters to make parentheses valid.

**The insight:** Mark indices of unmatched brackets during stack traversal, then skip them when building result.

**Structure:**
```java
class Solution {
    public String minRemoveToMakeValid(String s) {
        Set<Integer> toRemove = new HashSet<>();
        Deque<Integer> stack = new ArrayDeque<>();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') {
                stack.push(i);
            } else if (c == ')') {
                if (stack.isEmpty()) {
                    toRemove.add(i);
                } else {
                    stack.pop();
                }
            }
        }
        while (!stack.isEmpty()) {
            toRemove.add(stack.pop());
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (!toRemove.contains(i)) {
                result.append(s.charAt(i));
            }
        }
        return result.toString();
    }
}
```

**Time:** O(n), **Space:** O(n)

---

**Problem 1c: LC 1614 Maximum Nesting Depth of the Parentheses**

**Problem:** Track the maximum nesting depth of parentheses.

**The insight:** Stack size at any point = current nesting depth. Track max.

**Structure:**
```java
class Solution {
    public int maxDepth(String s) {
        int maxDepth = 0;
        Deque<Character> stack = new ArrayDeque<>();
        for (char c : s.toCharArray()) {
            if (c == '(' || c == '{' || c == '[') {
                stack.push(c);
                maxDepth = Math.max(maxDepth, stack.size());
            } else if (c == ')' || c == '}' || c == ']') {
                if (!stack.isEmpty()) {
                    stack.pop();
                }
            }
        }
        return maxDepth;
    }
}
```

**Time:** O(n), **Space:** O(n)

---

### Pattern 2 — Monotonic Stack (Next Greater Element Family)

**When you'll see this pattern:**
- LC 496 Next Greater Element I — next greater to the right
- LC 739 Daily Temperatures — days until warmer temperature
- LC 84 Largest Rectangle in Histogram — heights with area calculation
- LC 42 Trapping Rain Water — water trapped between heights
- Real-world example: Stock price analysis (when does price exceed current?), height-based problems

**Problem motivation — concrete example:**

"Given an integer array `nums`, for each element, find the next greater element to its right. If no such element exists, return -1."

Example: `nums = [1, 5, 0, 3, 4, 5]` → `[-1, -1, 3, 4, 5, -1]`

**Naive approach (and why it fails):**

```java
// Brute: for each element, scan right part for next greater
// Time: O(n²) — for each element, scan ~n elements
// Space: O(1)
// Problem: On LC 496 (n=10k) → 100M operations → TLE
```

**Why this pattern solves it:**

Iterate right-to-left, maintaining a stack of elements in decreasing order. When you encounter a new element, pop all smaller elements (they can never be the next greater for themselves). Stack top is the next greater. **Key insight: if current element is smaller than stack top, then stack top is the answer. If larger, pop all smaller elements and the new top is the answer.**

**Steps in plain English:**

1. Create an empty stack.
2. Iterate through array RIGHT-TO-LEFT.
3. For each element:
   - Pop all stack elements ≤ current (they're useless now).
   - Stack top is the next greater element (or -1 if empty).
   - Push current for future comparisons.

```java
// Monotonic stack pattern (next greater element)
Deque<Integer> stack = new ArrayDeque<>();
int[] result = new int[nums.length];

for (int i = nums.length - 1; i >= 0; i--) {
    // Step 3: pop all smaller or equal
    while (!stack.isEmpty() && stack.peek() <= nums[i]) {
        stack.pop();
    }
    
    // Stack top is next greater (or -1)
    result[i] = stack.isEmpty() ? -1 : stack.peek();
    
    // Push current for smaller elements to compare
    stack.push(nums[i]);
}
```

**Why this works:** Right-to-left ensures all elements to the right are already processed. Monotonic decreasing order means we quickly find the next greater without rescanning.

---

> 🧩 **Drill:**
> Write code to find the previous greater element (to the left) for each element.

<details>
<summary>Solution</summary>

```java
Deque<Integer> stack = new ArrayDeque<>();
int[] result = new int[nums.length];

// Iterate left-to-right for previous greater
for (int i = 0; i < nums.length; i++) {
    while (!stack.isEmpty() && stack.peek() <= nums[i]) {
        stack.pop();
    }
    result[i] = stack.isEmpty() ? -1 : stack.peek();
    stack.push(nums[i]);
}
return result;
```
</details>

---

### Pattern 2 — Pattern Application Gallery

**Problem 2a: LC 496 Next Greater Element I**

**Problem:** Find next greater element to the right for each element.

**Naive approach:**
```java
// Brute: nested loop, for each element scan right
// Time: O(n²), Space: O(1)
for (int i = 0; i < nums.length; i++) {
    result[i] = -1;
    for (int j = i + 1; j < nums.length; j++) {
        if (nums[j] > nums[i]) {
            result[i] = nums[j];
            break;
        }
    }
}
```

**The insight:** Monotonic stack right-to-left avoids rescanning.

**Structure:**
```java
class Solution {
    public int[] nextGreaterElement(int[] nums) {
        Deque<Integer> stack = new ArrayDeque<>();
        int[] result = new int[nums.length];
        for (int i = nums.length - 1; i >= 0; i--) {
            while (!stack.isEmpty() && stack.peek() <= nums[i]) {
                stack.pop();
            }
            result[i] = stack.isEmpty() ? -1 : stack.peek();
            stack.push(nums[i]);
        }
        return result;
    }
}
```

**Time:** O(n), **Space:** O(n)

---

**Problem 2b: LC 739 Daily Temperatures**

**Problem:** For each day, find how many days until a warmer temperature.

**The insight:** Use monotonic stack to track indices. When you find a warmer day, calculate the gap.

**Structure:**
```java
class Solution {
    public int[] dailyTemperatures(int[] temperatures) {
        int[] result = new int[temperatures.length];
        Deque<Integer> stack = new ArrayDeque<>();
        for (int i = temperatures.length - 1; i >= 0; i--) {
            while (!stack.isEmpty() && temperatures[stack.peek()] <= temperatures[i]) {
                stack.pop();
            }
            result[i] = stack.isEmpty() ? 0 : stack.peek() - i;
            stack.push(i);
        }
        return result;
    }
}
```

**Time:** O(n), **Space:** O(n)

---

**Problem 2c: LC 84 Largest Rectangle in Histogram**

**Problem:** Given heights, find the largest rectangular area.

**Naive approach:**
```java
// Brute: for each bar, extend left and right until shorter bar
// Time: O(n²), Space: O(1)
for (int i = 0; i < heights.length; i++) {
    int left = i, right = i;
    while (left > 0 && heights[left - 1] >= heights[i]) {
        left--;
    }
    while (right < heights.length - 1 && heights[right + 1] >= heights[i]) {
        right++;
    }
    int area = heights[i] * (right - left + 1);
    maxArea = Math.max(maxArea, area);
}
```

**The insight:** Use monotonic stack to find left and right boundaries for each bar in O(n).

**Structure:**
```java
class Solution {
    public int largestRectangleArea(int[] heights) {
        Deque<Integer> stack = new ArrayDeque<>();
        int maxArea = 0;
        for (int i = 0; i < heights.length; i++) {
            while (!stack.isEmpty() && heights[stack.peek()] > heights[i]) {
                int h = heights[stack.pop()];
                int w = stack.isEmpty() ? i : i - stack.peek() - 1;
                maxArea = Math.max(maxArea, h * w);
            }
            stack.push(i);
        }
        while (!stack.isEmpty()) {
            int h = heights[stack.pop()];
            int w = stack.isEmpty() ? heights.length : heights.length - stack.peek() - 1;
            maxArea = Math.max(maxArea, h * w);
        }
        return maxArea;
    }
}
```

**Time:** O(n), **Space:** O(n)

---

### Pattern 3 — Min Stack (Track Minimum While Supporting Pop)

**When you'll see this pattern:**
- LC 155 Min Stack — get min in O(1)
- LC 1944 Number of Visible People in a Queue — next greater + min stack combo
- Real-world example: Stock portfolio (track min price seen so far while popping transactions)

**Problem motivation — concrete example:**

"Design a stack that supports push, pop, top, and retrieving the minimum element in O(1) time."

Example: push(3) → push(2) → getMin()=2 → pop() → getMin()=3

**Naive approach (and why it fails):**

```java
// Brute: maintain array of values, search for min on getMin()
// Time: push/pop O(1), getMin() O(n)
// Problem: Interview expects O(1) getMin, so this fails
```

**Why this pattern solves it:**

Maintain TWO parallel stacks: one for values, one tracking the minimum at each depth. Every push/pop touches both stacks in O(1). **Key insight: at any depth d, we can instantly know the min value for all elements from depth 0 to d.**

**Steps in plain English:**

1. Maintain TWO stacks: value stack + min-at-this-depth stack.
2. On push: add value to both stacks. For min stack, push min(new value, current min).
3. On pop: pop from both stacks.
4. On getMin: peek min stack.

```java
// Min stack pattern (dual stack)
class MinStack {
    Deque<Integer> stack = new ArrayDeque<>();
    Deque<Integer> minStack = new ArrayDeque<>();
    
    public void push(int val) {
        stack.push(val);
        // Step 2: min stack gets min of new value and current min
        minStack.push(Math.min(minStack.isEmpty() ? val : minStack.peek(), val));
    }
    
    public void pop() {
        // Step 3: pop both
        stack.pop();
        minStack.pop();
    }
    
    public int getMin() {
        // Step 4: peek min stack
        return minStack.peek();
    }
}
```

**Why this works:** Parallel min-stack ensures O(1) getMin without rescanning.

---

> 🧩 **Drill:**
> Implement a stack that supports push, pop, top, and getMin in O(1).

<details>
<summary>Solution</summary>

```java
Deque<Integer> stack = new ArrayDeque<>();
Deque<Integer> minStack = new ArrayDeque<>();

void push(int x) {
    stack.push(x);
    minStack.push(Math.min(minStack.isEmpty() ? x : minStack.peek(), x));
}

void pop() {
    stack.pop();
    minStack.pop();
}

int getMin() {
    return minStack.peek();
}
```
</details>

---

### Pattern 3 — Pattern Application Gallery

**Problem 3a: LC 155 Min Stack**

**Problem:** Design stack with push, pop, top, getMin all O(1).

**Structure:**
```java
class MinStack {
    Deque<Integer> stack = new ArrayDeque<>();
    Deque<Integer> minStack = new ArrayDeque<>();
    
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
        return minStack.peek();
    }
}
```

**Time:** All operations O(1), **Space:** O(n)

---

**Problem 3b: LC 1944 Number of Visible People in a Queue**

**Problem:** For each person, count how many people to their right they can see (taller people block view).

**The insight:** Use monotonic stack (decreasing) to track visible people.

**Structure:**
```java
class Solution {
    public int[] canSeePersonsCount(int[] heights) {
        int n = heights.length;
        int[] result = new int[n];
        Deque<Integer> stack = new ArrayDeque<>();
        for (int i = n - 1; i >= 0; i--) {
            int count = 0;
            while (!stack.isEmpty() && heights[stack.peek()] < heights[i]) {
                count++;
                stack.pop();
            }
            if (!stack.isEmpty()) {
                count++;  // can see the tall person
            }
            result[i] = count;
            stack.push(i);
        }
        return result;
    }
}
```

**Time:** O(n), **Space:** O(n)

---

### Pattern 4 — Sliding Window Maximum (Monotonic Deque)

**When you'll see this pattern:**
- LC 239 Sliding Window Maximum — max in each k-size window
- LC 1438 Longest Continuous Subarray With Absolute Diff ≤ Limit — track min/max in window
- Real-world example: Real-time data streaming (what's the max temperature in the last hour?)

**Problem motivation — concrete example:**

"Given an array `nums` and an integer `k`, return an array of the maximum element in each window of size `k`."

Example: `nums = [1, 3, 1, 2, 0, 5]`, `k = 3` → `[3, 3, 2, 5]`

**Naive approach (and why it fails):**

```java
// Brute: for each window, scan all k elements for max
// Time: O(n * k) — for n windows, k comparisons each
// Space: O(1)
// Problem: On LC 239 (n=100k, k=20k) → 2B operations → TLE
```

**Why this pattern solves it:**

Use a DEQUE (double-ended queue) to store indices of useful elements in decreasing order of values. Each element is added once and removed once, so O(n) total. **Key insight: we only need to track elements that could ever be the max in a future window.**

**Steps in plain English:**

1. Use a DEQUE to store indices (not values) in decreasing order.
2. For each new element:
   - Remove indices outside the current window (front of deque).
   - Remove indices whose values are ≤ current (back of deque) — they'll never be max in future windows.
   - Add current index to back.
   - Window max is at front of deque.

```java
// Sliding window maximum pattern (monotonic deque)
Deque<Integer> deque = new ArrayDeque<>();  // stores indices
int[] result = new int[nums.length - k + 1];
int resultIdx = 0;

for (int i = 0; i < nums.length; i++) {
    // Step 2a: remove indices outside window
    while (!deque.isEmpty() && deque.peekFirst() < i - k + 1) {
        deque.removeFirst();
    }
    
    // Step 2b: remove indices with value ≤ current
    while (!deque.isEmpty() && nums[deque.peekLast()] <= nums[i]) {
        deque.removeLast();
    }
    
    // Step 2c: add current index
    deque.addLast(i);
    
    // Step 2d: window max is at front
    if (i >= k - 1) {
        result[resultIdx++] = nums[deque.peekFirst()];
    }
}
```

**Why this works:** Monotonic decreasing in the deque ensures O(1) amortized per element. Each element is added once and removed once.

---

> 🧩 **Drill:**
> Write code to find the max in each window of size k.

<details>
<summary>Solution</summary>

```java
Deque<Integer> deque = new ArrayDeque<>();
int[] result = new int[nums.length - k + 1];
int idx = 0;

for (int i = 0; i < nums.length; i++) {
    // Remove out-of-window indices
    if (!deque.isEmpty() && deque.peek() < i - k + 1) {
        deque.poll();
    }
    
    // Remove smaller elements
    while (!deque.isEmpty() && nums[deque.peekLast()] <= nums[i]) {
        deque.pollLast();
    }
    
    deque.offer(i);
    
    if (i >= k - 1) {
        result[idx++] = nums[deque.peek()];
    }
}
return result;
```
</details>

---

### Pattern 4 — Pattern Application Gallery

**Problem 4a: LC 239 Sliding Window Maximum**

**Problem:** Find max in each k-size window.

**Naive approach:**
```java
// Brute: for each window, scan k elements
// Time: O(n * k), Space: O(1)
```

**The insight:** Monotonic deque stores indices in decreasing order, avoiding rescans.

**Structure:**
```java
class Solution {
    public int[] maxSlidingWindow(int[] nums, int k) {
        Deque<Integer> deque = new ArrayDeque<>();
        int[] result = new int[nums.length - k + 1];
        int resultIdx = 0;
        for (int i = 0; i < nums.length; i++) {
            if (!deque.isEmpty() && deque.peekFirst() < i - k + 1) {
                deque.removeFirst();
            }
            while (!deque.isEmpty() && nums[deque.peekLast()] <= nums[i]) {
                deque.removeLast();
            }
            deque.addLast(i);
            if (i >= k - 1) {
                result[resultIdx++] = nums[deque.peekFirst()];
            }
        }
        return result;
    }
}
```

**Time:** O(n), **Space:** O(k)

---

**Problem 4b: LC 1438 Longest Continuous Subarray With Absolute Diff ≤ Limit**

**Problem:** Find longest subarray where max - min ≤ limit.

**The insight:** Use two monotonic deques (min and max) to track min/max in window. Shrink window from left until constraint is satisfied.

**Structure:**
```java
class Solution {
    public int longestSubarray(int[] nums, int limit) {
        Deque<Integer> minDeque = new ArrayDeque<>();
        Deque<Integer> maxDeque = new ArrayDeque<>();
        int left = 0, maxLen = 0;
        for (int right = 0; right < nums.length; right++) {
            while (!minDeque.isEmpty() && nums[minDeque.peekLast()] >= nums[right]) {
                minDeque.pollLast();
            }
            while (!maxDeque.isEmpty() && nums[maxDeque.peekLast()] <= nums[right]) {
                maxDeque.pollLast();
            }
            minDeque.addLast(right);
            maxDeque.addLast(right);
            while (nums[maxDeque.peekFirst()] - nums[minDeque.peekFirst()] > limit) {
                if (minDeque.peekFirst() == left) {
                    minDeque.pollFirst();
                }
                if (maxDeque.peekFirst() == left) {
                    maxDeque.pollFirst();
                }
                left++;
            }
            maxLen = Math.max(maxLen, right - left + 1);
        }
        return maxLen;
    }
}
```

**Time:** O(n), **Space:** O(n)

---

### Pattern 5 — Level-Order BFS (Queue)

**When you'll see this pattern:**
- LC 102 Binary Tree Level Order Traversal — group nodes by level
- LC 103 Binary Tree Zigzag Level Order Traversal — alternate zigzag direction
- LC 200 Number of Islands — BFS to explore connected components
- Real-world example: Graph exploration, network analysis (neighbors at distance k)

**Problem motivation — concrete example:**

"Given a binary tree, return its level-order traversal (nodes grouped by level)."

Example: Tree with root 3, children 9, 20 (which has children 15, 7) → [[3], [9, 20], [15, 7]]

**Naive approach (and why it fails):**

```java
// Recursive DFS: traverse and tag each node with its depth
// Time: O(n), Space: O(h) recursion depth
// Works but harder to think about; queue is more intuitive
```

**Why this pattern solves it:**

A queue processes nodes level-by-level. At each iteration, we process all nodes currently in the queue (one level), then add their children (next level). **Key insight: queue's FIFO property naturally groups nodes by level.**

**Steps in plain English:**

1. Create a queue.
2. Add root to queue.
3. While queue not empty:
   - Process all nodes currently in queue (one level).
   - For each node, add its children to queue (next level).

```java
// Level-order BFS pattern
Queue<TreeNode> queue = new ArrayDeque<>();
queue.offer(root);

while (!queue.isEmpty()) {
    int levelSize = queue.size();  // important: process one level at a time
    List<Integer> level = new ArrayList<>();
    
    // Step 3: process all nodes at current level
    for (int i = 0; i < levelSize; i++) {
        TreeNode node = queue.poll();
        level.add(node.val);
        
        // Add children to queue for next level
        if (node.left != null) {
            queue.offer(node.left);
        }
        if (node.right != null) {
            queue.offer(node.right);
        }
    }
    
    result.add(level);
}
```

**Why this works:** Queue ensures we process all nodes at depth d before any node at depth d+1. No explicit depth tracking needed.

---

> 🧩 **Drill:**
> Write code for level-order traversal of a binary tree.

<details>
<summary>Solution</summary>

```java
List<List<Integer>> result = new ArrayList<>();
if (root == null) {
    return result;
}
Queue<TreeNode> queue = new ArrayDeque<>();
queue.offer(root);
while (!queue.isEmpty()) {
    int size = queue.size();
    List<Integer> level = new ArrayList<>();
    for (int i = 0; i < size; i++) {
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
```
</details>

---

### Pattern 5 — Pattern Application Gallery

**Problem 5a: LC 102 Binary Tree Level Order Traversal**

**Problem:** Return nodes grouped by level.

**Structure:**
```java
class Solution {
    public List<List<Integer>> levelOrder(TreeNode root) {
        List<List<Integer>> result = new ArrayList<>();
        if (root == null) {
            return result;
        }
        Queue<TreeNode> queue = new ArrayDeque<>();
        queue.offer(root);
        while (!queue.isEmpty()) {
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
            result.add(level);
        }
        return result;
    }
}
```

**Time:** O(n), **Space:** O(w) (w = max width)

---

**Problem 5b: LC 103 Binary Tree Zigzag Level Order Traversal**

**Problem:** Level-order but alternate left-to-right, right-to-left.

**The insight:** Use deque instead of queue, and alternate between addFirst/addLast based on level parity.

**Structure:**
```java
class Solution {
    public List<List<Integer>> zigzagLevelOrder(TreeNode root) {
        List<List<Integer>> result = new ArrayList<>();
        if (root == null) {
            return result;
        }
        Queue<TreeNode> queue = new ArrayDeque<>();
        queue.offer(root);
        boolean leftToRight = true;
        while (!queue.isEmpty()) {
            int levelSize = queue.size();
            Deque<Integer> level = new LinkedList<>();
            for (int i = 0; i < levelSize; i++) {
                TreeNode node = queue.poll();
                if (leftToRight) {
                    level.addLast(node.val);
                } else {
                    level.addFirst(node.val);
                }
                if (node.left != null) {
                    queue.offer(node.left);
                }
                if (node.right != null) {
                    queue.offer(node.right);
                }
            }
            result.add(new ArrayList<>(level));
            leftToRight = !leftToRight;
        }
        return result;
    }
}
```

**Time:** O(n), **Space:** O(w)

---

**Problem 5c: LC 200 Number of Islands**

**Problem:** Count connected components of '1's in a grid.

**The insight:** Use BFS with queue to explore each island.

**Structure:**
```java
class Solution {
    public int numIslands(char[][] grid) {
        if (grid == null || grid.length == 0) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < grid.length; i++) {
            for (int j = 0; j < grid[0].length; j++) {
                if (grid[i][j] == '1') {
                    count++;
                    bfs(grid, i, j);
                }
            }
        }
        return count;
    }
    
    private void bfs(char[][] grid, int i, int j) {
        Queue<int[]> queue = new ArrayDeque<>();
        queue.offer(new int[]{i, j});
        grid[i][j] = '0';
        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            int x = cell[0], y = cell[1];
            int[][] dirs = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
            for (int[] dir : dirs) {
                int nx = x + dir[0], ny = y + dir[1];
                if (nx >= 0 && nx < grid.length && ny >= 0 && ny < grid[0].length && grid[nx][ny] == '1') {
                    queue.offer(new int[]{nx, ny});
                    grid[nx][ny] = '0';
                }
            }
        }
    }
}
```

**Time:** O(m * n), **Space:** O(m * n)

---

## 🔬 Worked Walkthroughs

### WW-1 — LC 20 Valid Parentheses

**Problem statement:** Given a string of `(){}[]` characters, return `true` if every opening bracket is closed by the correct type in the correct order.

**Brute force:** Try to enumerate all balanced bracket strings of length n and check membership — O(Catalan(n)) which is exponential. A counter tracking depth can't distinguish type mismatches (`"({)}"` counts as balanced but isn't). There is no simpler-than-stack correct approach.

**Intuition bridge:** A stack naturally holds the most recently opened unmatched bracket; when a closing bracket arrives, only the stack top can be its legal match — if it isn't, the string is invalid.

**Steps in plain English:**

1. **Build a close→open map** so you can look up the expected opening bracket for any closing bracket.
2. **Scan left to right.** Closing bracket → pop the stack and compare; mismatch or empty stack → return false. Opening bracket → push.
3. **After the scan** the stack must be empty — any leftover means an unmatched open bracket.

```java
class Solution {
    public boolean isValid(String s) {
        // Step 1 — closing → expected-open map
        Map<Character, Character> map = Map.of(')', '(', '}', '{', ']', '[');
        Deque<Character> stack = new ArrayDeque<>();
        for (char c : s.toCharArray()) {
            if (map.containsKey(c)) {
                // Step 2 — close bracket: pop and match
                if (stack.isEmpty() || stack.pop() != map.get(c)) {
                    return false;
                }
            } else {
                // Step 2 — open bracket: push for future match
                stack.push(c);
            }
        }
        // Step 3 — all opens must have been matched
        return stack.isEmpty();
    }
}
```

**Complexity:** Time O(n), Space O(n).

**Transfers to:**

| What's identical | ONE thing different | Key line that changes |
| --- | --- | --- |
| LC 1614 Maximum Nesting Depth of Parentheses | Only `()`; track depth not match | `if (c == '(') depth++; else if (c == ')') depth--;` |
| LC 1541 Minimum Insertions to Balance Parentheses | Count needed insertions instead of validate | Track open count; add insertions when close arrives early |
| LC 856 Score of Parentheses | Push score onto stack instead of bracket chars | `if (c == ')') { int v = stack.pop() == 0 ? 1 : 2 * stack.pop(); stack.push(v); }` |

---

### WW-2 — LC 155 Min Stack

**Problem statement:** Design a stack supporting push, pop, top, and getMin — all in O(1) time.

**Brute force:** A single stack gives push/pop/top in O(1) but getMin requires scanning all elements — O(n) per call. Keeping a global min breaks on pop: when the min is popped, you don't know the next minimum without a full scan.

**Intuition bridge:** Each element in the stack has a "minimum at this depth" that is fixed at push time and never changes — a parallel min-stack stores exactly that value at each level, so getMin is always a single peek.

**Steps in plain English:**

1. **Two stacks:** `stack` for main values, `minStack` for the running minimum at each depth.
2. **On push:** push `val` to the main stack; push `min(val, minStack.peek())` to the min-stack (just `val` if it is empty).
3. **On pop:** pop both stacks in lock-step so they stay in sync.
4. **getMin** returns `minStack.peek()` — no scan needed.

```java
class MinStack {
    // Step 1 — two parallel stacks
    Deque<Integer> stack = new ArrayDeque<>();
    Deque<Integer> minStack = new ArrayDeque<>();

    public void push(int val) {
        // Step 2 — push val; push current minimum at this depth
        stack.push(val);
        int currentMin = minStack.isEmpty() ? val : Math.min(minStack.peek(), val);
        minStack.push(currentMin);
    }

    public void pop() {
        // Step 3 — pop both stacks in lock-step
        stack.pop();
        minStack.pop();
    }

    public int top() {
        return stack.peek();
    }

    public int getMin() {
        // Step 4 — minimum at current depth is always at minStack top
        return minStack.peek();
    }
}
```

**Complexity:** All operations O(1), Space O(n).

**Transfers to:**

| What's identical | ONE thing different | Key line that changes |
| --- | --- | --- |
| LC 716 Max Stack | Track maximum instead of minimum; also need `popMax()` | `Math.max(maxStack.peek(), val)` for push; scan for popMax |
| Max-Min Stack | Track both simultaneously | Two shadow stacks — `minStack` and `maxStack` in parallel |
| LC 895 Maximum Frequency Stack | Track which element appears most; pop the most-frequent | Frequency map + group map; push `val` into `group[freq[val]]` |

---

### WW-3 — LC 239 Sliding Window Maximum

**Problem statement:** Given an integer array and window size `k`, return the maximum value in each sliding window of size `k`.

**Brute force:** For each of the n−k+1 windows, scan all k elements to find the max — O(nk) time. With n=10^5 and k=10^4 this times out.

**Intuition bridge:** A smaller element behind a larger one in the same window can never be the answer for any future window — discard it immediately. A monotonically decreasing deque (storing indices) keeps the window maximum at its front at all times.

**Steps in plain English:**

1. **Monotonic deque of indices** (values at those indices are non-increasing from front to back).
2. **Evict stale front** if its index has fallen outside the window (`< i − k + 1`).
3. **Evict smaller rear elements** while `nums[rear] ≤ nums[i]` — they are dominated and will never be the max.
4. **Add `i` to rear.** Once `i ≥ k − 1`, emit `nums[deque.front()]` as the window max.

```java
class Solution {
    public int[] maxSlidingWindow(int[] nums, int k) {
        int n = nums.length;
        int[] result = new int[n - k + 1];
        // Step 1 — monotonic deque of indices (decreasing by value)
        Deque<Integer> deque = new ArrayDeque<>();
        for (int i = 0; i < n; i++) {
            // Step 2 — evict stale front (out of window)
            if (!deque.isEmpty() && deque.peekFirst() < i - k + 1) {
                deque.pollFirst();
            }
            // Step 3 — evict smaller rear elements (dominated, never the max)
            while (!deque.isEmpty() && nums[deque.peekLast()] <= nums[i]) {
                deque.pollLast();
            }
            deque.offerLast(i);
            // Step 4 — emit max once window is full
            if (i >= k - 1) {
                result[i - k + 1] = nums[deque.peekFirst()];
            }
        }
        return result;
    }
}
```

**Complexity:** Time O(n) amortized (each index enqueued and dequeued at most once), Space O(k).

**Transfers to:**

| What's identical | ONE thing different | Key line that changes |
| --- | --- | --- |
| LC 862 Shortest Subarray with Sum ≥ K | Monotonic deque on prefix sums; pop front when sum condition met | `while (!deque.isEmpty() && prefix[i] - prefix[deque.peekFirst()] >= k) ans = min(ans, i - deque.pollFirst());` |
| LC 1499 Max Value of Equation | Sliding window max on `y[i] + x[i]`; evict by absolute x-distance instead of index count | Evict front when `x[i] - x[deque.front()] > k` |
| LC 2762 Continuous Subarrays | Maintain both min and max deques simultaneously; window valid while `max − min ≤ 2` | Two deques — `maxDeque` and `minDeque`; shrink left when constraint breaks |

---

### WW-4 — LC 739 Daily Temperatures

**Problem statement:** Given an array of daily temperatures, for each day return the number of days until a warmer temperature (0 if none exists).

**Brute force:** For each day `i`, scan every subsequent day `j > i` until `temps[j] > temps[i]` — O(n²) time.

**Intuition bridge:** Process temperatures left to right, keeping a stack of "waiting" days (those that haven't found their warmer day yet). When day `i` is warmer than the day at the stack top, the top day's wait is `i − top` — pop, record, and keep popping until the stack is back in order.

**Steps in plain English:**

1. **Monotonic decreasing stack of indices** — temperatures at stacked indices are non-increasing from bottom to top.
2. **For each index `i`:** while the stack is non-empty and `temps[i] > temps[stack.peek()]`, pop index `j`, set `ans[j] = i − j`.
3. **Push `i`** as a new waiting candidate.
4. **Any indices still on the stack** at the end have no warmer day — `ans[j]` stays 0.

```java
class Solution {
    public int[] dailyTemperatures(int[] temperatures) {
        int n = temperatures.length;
        int[] ans = new int[n];
        // Step 1 — monotonic decreasing stack of indices
        Deque<Integer> stack = new ArrayDeque<>();
        for (int i = 0; i < n; i++) {
            // Step 2 — pop all days whose warmer day has arrived
            while (!stack.isEmpty() && temperatures[i] > temperatures[stack.peek()]) {
                int j = stack.pop();
                ans[j] = i - j;
            }
            // Step 3 — push current day as a new waiting candidate
            stack.push(i);
        }
        // Step 4 — remaining indices: no warmer day; ans stays 0
        return ans;
    }
}
```

**Complexity:** Time O(n) amortized (each index pushed and popped at most once), Space O(n).

**Transfers to:**

| What's identical | ONE thing different | Key line that changes |
| --- | --- | --- |
| LC 496 Next Greater Element I | Same "next greater" pop trigger; answer is the value not the distance | `Map<Integer, Integer> result = new HashMap<>(); result.put(nums2[j], nums2[i]);` after pop |
| LC 503 Next Greater Element II (circular) | Traverse indices `0..2n−1 mod n` to simulate circular wrap | `int idx = i % n; temperatures[idx] ...` |
| LC 901 Online Stock Span | Pop when `prices[top] ≤ price` (non-decreasing); accumulate span count instead of distance | `while (!stack.isEmpty() && prices[stack.peek()] <= price) span += spans[stack.pop()];` |

---

### WW-5 — LC 84 Largest Rectangle in Histogram

**Problem statement:** Given an array of bar heights forming a histogram, find the area of the largest rectangle that fits entirely within it.

**Brute force:** For every pair of bars `(i, j)`, the rectangle height is `min(heights[i..j])` and width is `j − i + 1` — check all O(n²) pairs using a running min for O(n²) time.

**Intuition bridge:** A bar can extend left and right only as far as bars of equal or greater height surround it. Keep a monotonic increasing stack; when a shorter bar arrives, the bar at the stack top has found its right boundary — pop it, compute its maximal rectangle using the new stack top as the implicit left boundary.

**Steps in plain English:**

1. **Append a sentinel `0`** to the heights array — forces all remaining bars to be popped at the end of the loop.
2. **Monotonic increasing stack of indices** (heights non-decreasing from bottom to top).
3. **For each index `i`:** while `heights[i] < heights[stack.peek()]`, pop `top`; height = `heights[top]`; width = `i` if stack is empty, else `i − stack.peek() − 1`; update `maxArea`.
4. **Push `i`.**

```java
class Solution {
    public int largestRectangleArea(int[] heights) {
        int n = heights.length;
        // Step 1 — sentinel forces all bars to be popped at the end
        int[] h = new int[n + 1];
        System.arraycopy(heights, 0, h, 0, n);
        h[n] = 0;
        int maxArea = 0;
        // Step 2 — monotonic increasing stack of indices
        Deque<Integer> stack = new ArrayDeque<>();
        for (int i = 0; i <= n; i++) {
            // Step 3 — pop when current bar is shorter than stack top
            while (!stack.isEmpty() && h[i] < h[stack.peek()]) {
                int top = stack.pop();
                int height = h[top];
                // left boundary: new stack top + 1; right boundary: i - 1
                int width = stack.isEmpty() ? i : i - stack.peek() - 1;
                maxArea = Math.max(maxArea, height * width);
            }
            // Step 4 — push current index
            stack.push(i);
        }
        return maxArea;
    }
}
```

**Complexity:** Time O(n) amortized, Space O(n).

**Transfers to:**

| What's identical | ONE thing different | Key line that changes |
| --- | --- | --- |
| LC 85 Maximal Rectangle | Convert each row of a 2D `'0'/'1'` matrix into a histogram; run LC 84 per row | `heights[c] = matrix[r][c] == '1' ? heights[c] + 1 : 0;` then `largestRectangleArea(heights)` |
| LC 42 Trapping Rain Water | Monotonic stack — pop when taller bar found; accumulate trapped water instead of area | `water += (Math.min(h[i], h[stack.peek()]) - h[top]) * (i - stack.peek() - 1);` |
| LC 1793 Maximum Score of a Good Subarray | Same "min in range" pop-and-compute; restrict answer to subarrays containing index `k` | Guard update: `if (left <= k && k <= right) ans = Math.max(ans, height * width);` |

---

### WW-6 — LC 394 Decode String

**Problem statement:** Given an encoded string like `"3[a2[c]]"`, decode it so that `k[substring]` means `substring` repeated `k` times — nesting is possible.

**Brute force:** Locate the innermost `[...]` bracket pair, expand it, and repeat until no brackets remain. Each pass is O(n) and there can be O(n) levels of nesting — O(n²) worst case.

**Intuition bridge:** A stack of `(repeatCount, builtString)` pairs mirrors a recursive parser's call stack — push the current context before `[`, expand the inner segment, then pop-and-combine on `]`.

**Steps in plain English:**

1. **Two stacks:** `countStack` for pending repeat counts, `strStack` for the string built before each `[`. Also track `cur` (current segment) and `k` (current number).
2. **Digit:** accumulate `k = k * 10 + digit` (handles multi-digit numbers like `12`).
3. **`[`:** push `(k, cur)` onto the stacks; reset `k = 0` and `cur = ""` for the inner segment.
4. **`]`:** pop `repeatCount` and `prefix`; `cur = prefix + cur.repeat(repeatCount)`.
5. **Letter:** append to `cur`.

```java
class Solution {
    public String decodeString(String s) {
        // Step 1 — two stacks: pending counts and prefix strings
        Deque<Integer> countStack = new ArrayDeque<>();
        Deque<StringBuilder> strStack = new ArrayDeque<>();
        StringBuilder cur = new StringBuilder();
        int k = 0;
        for (char c : s.toCharArray()) {
            if (Character.isDigit(c)) {
                // Step 2 — accumulate multi-digit number
                k = k * 10 + (c - '0');
            } else if (c == '[') {
                // Step 3 — save context; reset for inner segment
                countStack.push(k);
                strStack.push(cur);
                k = 0;
                cur = new StringBuilder();
            } else if (c == ']') {
                // Step 4 — pop context; repeat inner segment and append to prefix
                int repeatCount = countStack.pop();
                StringBuilder prefix = strStack.pop();
                String inner = cur.toString();
                for (int i = 0; i < repeatCount; i++) {
                    prefix.append(inner);
                }
                cur = prefix;
            } else {
                // Step 5 — letter: append to current segment
                cur.append(c);
            }
        }
        return cur.toString();
    }
}
```

**Complexity:** Time O(output length) — each character written once, Space O(nesting depth).

**Transfers to:**

| What's identical | ONE thing different | Key line that changes |
| --- | --- | --- |
| LC 726 Number of Atoms | `(` / `)` with multiplier; accumulate element frequencies not strings | Push `Map<String, Integer>` per frame; merge maps on `)` |
| LC 385 Mini Parser (NestedInteger) | `[` / `]` delimit nested integer lists; build a `NestedInteger` tree not a string | `stack.push(new NestedInteger());` on `[`; `stack.peek().add(inner)` on `]` |
| LC 1003 Check If Word Is Valid After Substitutions | Only `abc` is a valid nested unit; validate rather than build | Check that every popped sequence equals `"abc"` |

---

### WW-7 — LC 150 Evaluate Reverse Polish Notation

**Problem statement:** Evaluate an arithmetic expression in Reverse Polish Notation (postfix) — operands appear before their operator, e.g. `["2","1","+","3","*"]` → 9.

**Brute force:** There is no simpler-than-stack brute force for RPN — it is inherently a left-to-right single-pass evaluation. A tree-based parse would reconstruct the full expression tree first, which is more complex, not less.

**Intuition bridge:** In RPN, when you see an operator, the two most recently seen unevaluated values are its operands. A stack gives you exactly those two with one pop each — push the result back and continue.

**Steps in plain English:**

1. **Scan tokens left to right.**
2. **Number token:** parse and push onto the stack.
3. **Operator token:** pop `b` (top), then pop `a` (second); compute `a op b`; push result. Note `b = stack.pop()` first — for `-` and `/`, order matters.
4. **Single value** remaining on the stack is the answer.

```java
class Solution {
    public int evalRPN(String[] tokens) {
        Deque<Integer> stack = new ArrayDeque<>();
        for (String token : tokens) {
            if ("+-*/".indexOf(token) >= 0) {
                // Step 3 — pop two operands (b is top, a is second); order matters for - and /
                int b = stack.pop();
                int a = stack.pop();
                int result;
                switch (token) {
                    case "+": result = a + b; break;
                    case "-": result = a - b; break;
                    case "*": result = a * b; break;
                    default:  result = a / b; break;
                }
                stack.push(result);
            } else {
                // Step 2 — operand: parse and push
                stack.push(Integer.parseInt(token));
            }
        }
        // Step 4 — single remaining value is the answer
        return stack.pop();
    }
}
```

**Complexity:** Time O(n), Space O(n).

**Transfers to:**

| What's identical | ONE thing different | Key line that changes |
| --- | --- | --- |
| LC 224 Basic Calculator | Infix `+`, `-`, `(`, `)` — push sign on `(`; apply sign on `)` | `stack.push(sign); sign = 1;` on `(`; `sign = stack.pop() * stack.pop();` on `)` |
| LC 227 Basic Calculator II | Infix `+`, `-`, `*`, `/` — no parens; handle precedence by pushing partial products | Push intermediate value for `*`/`/`; sum the stack at the end |
| LC 282 Expression Add Operators | Insert `+`, `-`, `*` between digits and evaluate candidates | Recursive DFS carrying `(index, prevVal, currVal, target)`; `currVal` replaces the stack |

---

### WW-8 — LC 232 Implement Queue using Stacks

**Problem statement:** Implement a FIFO queue using only two stacks, supporting push, pop, peek, and empty.

**Brute force:** Transfer all elements to a temp stack before each push (O(n) per push, O(1) pop) or before each pop (O(n) per pop, O(1) push). Either approach is correct but costs O(n) per operation.

**Intuition bridge:** Instead of transferring on every operation, be lazy — only drain the inbox stack into the outbox when the outbox is empty and a pop/peek is needed. Each element crosses from inbox to outbox exactly once, amortizing the cost to O(1) per operation.

**Steps in plain English:**

1. **Two stacks:** `inbox` receives all pushes; `outbox` serves all pops and peeks.
2. **Push:** always push to `inbox` — O(1).
3. **Pop/Peek:** if `outbox` is empty, drain all of `inbox` into `outbox` (reversal converts LIFO inbox order to FIFO outbox order); then pop/peek from `outbox`.
4. **Empty:** true only when both stacks are empty.

```java
class MyQueue {
    // Step 1 — inbox receives pushes; outbox serves pops
    Deque<Integer> inbox = new ArrayDeque<>();
    Deque<Integer> outbox = new ArrayDeque<>();

    public void push(int x) {
        // Step 2 — all new elements go to inbox
        inbox.push(x);
    }

    public int pop() {
        // Step 3 — lazy transfer on demand
        transfer();
        return outbox.pop();
    }

    public int peek() {
        transfer();
        return outbox.peek();
    }

    private void transfer() {
        if (outbox.isEmpty()) {
            while (!inbox.isEmpty()) {
                outbox.push(inbox.pop());
            }
        }
    }

    public boolean empty() {
        // Step 4 — truly empty only when both stacks are empty
        return inbox.isEmpty() && outbox.isEmpty();
    }
}
```

**Complexity:** Push O(1); pop/peek amortized O(1) — each element is pushed and popped at most once per stack. Space O(n).

**Transfers to:**

| What's identical | ONE thing different | Key line that changes |
| --- | --- | --- |
| LC 225 Implement Stack using Queues | Reverse problem: LIFO from one queue — rotate the queue after each push | `for (int i = 0; i < queue.size() - 1; i++) queue.offer(queue.poll());` |
| LC 641 Design Circular Deque | Insert/delete at both ends — circular array instead of two stacks | `int[] buf; int head, tail, size;` with modular arithmetic |
| LC 933 Number of Recent Calls | Queue as sliding window — evict stale timestamps on each request | `while (!queue.isEmpty() && queue.peek() < t - 3000) queue.poll(); return queue.size();` |

---

## ⚠️ Gotchas — Silent Bug Hall of Fame

### Gotcha 1 — LinkedList as Queue is Slower Than ArrayDeque

**The bug:**
```java
Queue<Integer> queue = new LinkedList<>();  // allocates Node per element
for (int i = 0; i < 1_000_000; i++) {
    queue.offer(i);  // new Node allocation overhead
    queue.poll();
}
// Slow due to allocation
```

**Prevention:**
```java
Queue<Integer> queue = new ArrayDeque<>();  // circular array, no allocation
for (int i = 0; i < 1_000_000; i++) {
    queue.offer(i);  // no per-element allocation
    queue.poll();
}
// Fast
```

---

### Gotcha 2 — PriorityQueue Iteration Is NOT Sorted

**The bug:**
```java
PriorityQueue<Integer> pq = new PriorityQueue<>();
pq.addAll(Arrays.asList(5, 2, 8, 1));

for (Integer x : pq) {
    System.out.println(x);  // prints 1, 5, 2, 8 (NOT sorted!)
}
```

**Why it breaks:**
- PriorityQueue only guarantees root is smallest (heap property, not sorted array).
- To get sorted output, you must poll() repeatedly.

**Prevention:**
```java
// Correct: poll one by one
List<Integer> sorted = new ArrayList<>();
while (!pq.isEmpty()) {
    sorted.add(pq.poll());  // [1, 2, 5, 8] sorted
}

// Or just sort if you don't need streaming
Arrays.sort(arr);
```

---

### Gotcha 3 — Stack Underflow (Pop from Empty)

**The bug:**
```java
Deque<Integer> stack = new ArrayDeque<>();
int val = stack.pop();  // throws NoSuchElementException
```

**Prevention:**
```java
if (!stack.isEmpty()) {
    int val = stack.pop();
}

// Or check peek first
Integer val = stack.peek();
if (val != null) {
    stack.pop();
}
```

---

## 🗺️ Practice Plan (in tiers)

### Tier 1 — Stack Basics ⭐

- ✅ LC 20 Valid Parentheses
- ✅ LC 155 Min Stack
- ✅ LC 1249 Minimum Remove to Make Valid Parentheses

### Tier 2 — Monotonic Stack ⭐

- ✅ LC 496 Next Greater Element I
- ✅ LC 739 Daily Temperatures
- 🟡 **Try after Tier 2** — LC 84 Largest Rectangle in Histogram (hard)
- 🟡 **Try after Tier 2** — LC 42 Trapping Rain Water (hard)

### Tier 3 — Queue & BFS ⭐

- ✅ LC 102 Binary Tree Level Order Traversal
- ✅ LC 103 Binary Tree Zigzag Level Order Traversal
- ✅ LC 200 Number of Islands

### Tier 4 — Sliding Window & Priority Queue ⭐

- ✅ LC 239 Sliding Window Maximum
- 🟡 **Try after Tier 4** — LC 1438 Longest Continuous Subarray With Absolute Diff ≤ Limit
- 🔴 **Reference Only** — LC 1825 Finding MK Average (complex multi-deque)

---

## 🧾 TL;DR — One-Page Summary

**When to Use:**
- **Stack (ArrayDeque):** LIFO, undo/redo, bracket matching, DFS, monotonic patterns
- **Queue (ArrayDeque):** FIFO, BFS, level-order, task scheduling
- **Deque:** Double-ended, useful for monotonic deque (sliding window max)
- **PriorityQueue:** Priority-based access, Kth largest, merge K sorted lists

**5 Core Patterns:**
1. **Bracket Matching** — stack for balanced parentheses
2. **Monotonic Stack** — next greater element, daily temperatures, largest rectangle
3. **Min Stack** — dual stack tracks minimum at each depth
4. **Sliding Window Max** — monotonic deque for O(n) window queries
5. **Level-Order BFS** — queue processes nodes level-by-level

**3 Silent Bugs:**
1. LinkedList queue (slow) → use ArrayDeque
2. PriorityQueue iteration (not sorted) → use poll()
3. Pop from empty (underflow) → check isEmpty first

**Complexity:**
- ArrayDeque stack/queue: O(1) push/pop/offer/poll
- PriorityQueue: O(log n) offer/poll, O(1) peek
- Monotonic stack: O(n) overall (each element processed once)
- Monotonic deque: O(1) amortized per element

---

## 🔗 Cross-References

- **HashMap patterns:** `DSA/DeepDive/hashmaps-fundamentals.md` — pattern-driven approach
- **Sets:** `DSA/DeepDive/sets-fundamentals.md` — membership checks (HashSet O(1))
- **Trees & BFS:** (future) `DSA/DeepDive/trees-fundamentals.md` — BFS uses queue
- **Graphs:** (future) — BFS/DFS use queues/stacks

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| June 2026 | **Deep dive rewritten with Pattern Application Gallery.** Now covers 5 core patterns (Bracket Matching, Monotonic Stack, Min Stack, Sliding Window Maximum, Level-Order BFS) with problem motivation structure (problem → naive approach → why pattern solves it → steps → code → drill). Pattern Application Gallery with 3 most-asked problems per pattern (selective naive approaches for Monotonic Stack and Sliding Window Maximum patterns where understanding is critical). 3 worked walkthroughs (LC 20, LC 239, LC 155). 3 gotchas with ❌/✅ code examples. 4-tier practice plan. Curriculum alignment (Striver, LeetCode editorials, GeeksforGeeks). |
