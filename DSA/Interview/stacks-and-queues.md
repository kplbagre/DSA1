# Stacks & Queues — Interview Playbook

> **Read this file when:** You have an interview in 1-2 days and need to recognize when a problem is secretly a stack or queue problem. Stacks especially are the "hidden pattern" — they show up in problems that don't mention "stack" anywhere.

---

## 🎯 Why You're Reading This

Stack and queue problems are deceptive. The problem never says "use a stack." It says "valid parentheses" or "daily temperatures" or "next greater element." Your job is to hear the trigger and reach for the right structure. This file builds that instinct.

---

## 🔧 Essential Methods — Know These Cold

| Method | What it does | Used in |
| --- | --- | --- |
| `Deque<E> stack = new ArrayDeque<>()` | Create a stack (prefer over `Stack<E>`) | All stack patterns |
| `stack.push(e)` | Push onto top — O(1) | All stack patterns |
| `stack.pop()` | Remove and return top — O(1) | All stack patterns |
| `stack.peek()` | View top without removing — O(1) | Patterns 2, 3 |
| `stack.isEmpty()` | Check if empty — O(1) | All stack patterns |
| `Queue<E> queue = new ArrayDeque<>()` | Create a queue (ArrayDeque faster than LinkedList) | Pattern 5 (design) |
| `queue.offer(e)` | Add to back — O(1) | Pattern 5 |
| `queue.poll()` | Remove from front — O(1) | Pattern 5 |
| `queue.peek()` | View front — O(1) | Pattern 5 |

> **Full reference:** `../Reference/arraydeque-and-queue-reference.md`

---

## 🧠 The Mental Model — When Does a Stack / Queue Appear?

```
Problem involves...
│
├── "Matching / nesting"
│   ├── Parentheses / brackets       → Stack matching (Pattern 1)
│   └── HTML tags / nested structure  → Stack matching (Pattern 1)
│
├── "Next greater / smaller element"
│   └── Monotonic Stack (Pattern 2) — THE most common stack interview pattern
│
├── "Evaluate expression"
│   └── Stack-based evaluation (Pattern 3)
│
├── "Undo / backtrack"
│   └── Stack as history (Pattern 4)
│
├── "Process in FIFO order"
│   └── Queue / BFS (see trees-and-bfs-dfs.md)
│
└── "Design a data structure"
    ├── "Stack with getMin()"  → Min Stack (Pattern 5)
    └── "Queue using stacks"   → Two-stack queue (Pattern 5)
```

**The stack mental model:** A stack remembers "things I haven't dealt with yet." Every time you push, you're saying "I'll deal with this later." Every time you pop, you're saying "now I can resolve this."

---

## 🧭 Pattern 1: Bracket Matching ⭐

**Recognition cues — reach for this when:**
- "Valid parentheses"
- "Match opening and closing brackets"
- "Remove outermost parentheses"
- Any nesting / matching problem

**Steps in plain English:**

1. **Walk the string** — for each character:
2. **Open bracket** → push onto stack.
3. **Close bracket** → check if stack is empty (invalid) or if top matches (pop). If mismatch → invalid.
4. **At end** → stack must be empty for valid.

```java
public boolean isValid(String s) {
    Deque<Character> stack = new ArrayDeque<>();

    for (char c : s.toCharArray()) {
        // Step 2 — push matching close bracket
        if (c == '(') {
            stack.push(')');
        } else if (c == '{') {
            stack.push('}');
        } else if (c == '[') {
            stack.push(']');
        }
        // Step 3 — check close bracket
        else if (stack.isEmpty() || stack.pop() != c) {
            return false;
        }
    }

    // Step 4 — stack must be empty
    return stack.isEmpty();
}
```

**Why push the EXPECTED close bracket?** Instead of pushing `'('` and later checking "does `')'` match `'('`?", push `')'` directly. Then the pop comparison is just `stack.pop() != c` — one check instead of three.

**🏷️ Problems:** LC 20 (Valid Parentheses), LC 1249 (Minimum Remove to Make Valid), LC 32 (Longest Valid Parentheses — advanced).

---

## 🧭 Pattern 2: Monotonic Stack (Next Greater / Smaller) ⭐

**Recognition cues — reach for this when:**
- "Next greater element"
- "Daily temperatures" (next warmer day)
- "Largest rectangle in histogram"
- "Stock span" (consecutive days ≤ today's price)
- Any problem asking "for each element, find the next element that is greater/smaller"

**The core idea:** Maintain a stack of "unresolved" elements (we haven't found their answer yet). When a new element arrives that resolves the top of the stack, pop and record the answer.

### 🎨 Visual — Monotonic Stack for Next Greater Element

```
Array: [4, 2, 1, 5, 3]
Stack holds INDICES of unresolved elements.

i=0: val=4  stack=[]        → push 0.          stack=[0]
i=1: val=2  stack=[0]       → 2 < 4, push 1.   stack=[0,1]
i=2: val=1  stack=[0,1]     → 1 < 2, push 2.   stack=[0,1,2]
i=3: val=5  stack=[0,1,2]   → 5 > 1: pop 2, ans[2]=5
                             → 5 > 2: pop 1, ans[1]=5
                             → 5 > 4: pop 0, ans[0]=5
                             → push 3.          stack=[3]
i=4: val=3  stack=[3]       → 3 < 5, push 4.   stack=[3,4]

End: remaining in stack have no next greater → ans[3]=-1, ans[4]=-1

Result: [5, 5, 5, -1, -1]
```

**KEY INVARIANT:** The stack always maintains a monotonically decreasing sequence (from bottom to top). When a new element breaks this monotonicity, it's the "next greater" for everything it pops.

**Steps in plain English:**

1. **Walk left to right** — for each element:
2. **While stack is not empty AND current > stack top** → pop; the current element is the answer for the popped index.
3. **Push current index** onto stack.
4. **Remaining stack elements** have no next greater → set to -1.

```java
public int[] dailyTemperatures(int[] temperatures) {
    int n = temperatures.length;
    int[] answer = new int[n];
    Deque<Integer> stack = new ArrayDeque<>();

    for (int i = 0; i < n; i++) {
        // Step 2 — resolve all elements smaller than current
        while (!stack.isEmpty() && temperatures[i] > temperatures[stack.peek()]) {
            int idx = stack.pop();
            answer[idx] = i - idx;
        }

        // Step 3 — push current (unresolved)
        stack.push(i);
    }
    // Step 4 — remaining are 0 by default (no warmer day)
    return answer;
}
```

**🏷️ Problems:** LC 739 (Daily Temperatures), LC 496 (Next Greater Element I), LC 503 (Next Greater Element II — circular, iterate 2×n), LC 84 (Largest Rectangle in Histogram — advanced).

---

## 🧭 Pattern 3: Expression Evaluation

**Recognition cues — reach for this when:**
- "Evaluate reverse Polish notation"
- "Basic calculator"
- "Decode string" (nested brackets)

### Reverse Polish Notation (LC 150):

Numbers go on stack. Operators pop two, compute, push result.

```java
public int evalRPN(String[] tokens) {
    Deque<Integer> stack = new ArrayDeque<>();

    for (String token : tokens) {
        if ("+-*/".contains(token)) {
            int b = stack.pop();
            int a = stack.pop();
            if ("+".equals(token)) {
                stack.push(a + b);
            } else if ("-".equals(token)) {
                stack.push(a - b);
            } else if ("*".equals(token)) {
                stack.push(a * b);
            } else {
                stack.push(a / b);
            }
        } else {
            stack.push(Integer.parseInt(token));
        }
    }
    return stack.pop();
}
```

### Decode String (LC 394):

`"3[a2[c]]"` → `"accaccacc"`. Use two stacks: one for counts, one for strings.

```java
public String decodeString(String s) {
    Deque<Integer> countStack = new ArrayDeque<>();
    Deque<StringBuilder> strStack = new ArrayDeque<>();
    StringBuilder current = new StringBuilder();
    int k = 0;

    for (char c : s.toCharArray()) {
        if (Character.isDigit(c)) {
            k = k * 10 + (c - '0');
        } else if (c == '[') {
            countStack.push(k);
            strStack.push(current);
            current = new StringBuilder();
            k = 0;
        } else if (c == ']') {
            int repeat = countStack.pop();
            StringBuilder prev = strStack.pop();
            for (int i = 0; i < repeat; i++) {
                prev.append(current);
            }
            current = prev;
        } else {
            current.append(c);
        }
    }
    return current.toString();
}
```

**🏷️ Problems:** LC 150 (Evaluate Reverse Polish Notation), LC 394 (Decode String), LC 224 (Basic Calculator — advanced).

---

## 🧭 Pattern 4: Stack as History (Undo / Backtrack)

**Recognition cues — reach for this when:**
- "Simplify file path" (Unix path with `.` and `..`)
- "Remove all adjacent duplicates"
- "Backspace string compare"
- "Asteroid collision"

The stack tracks "the current state." New elements either add to it or undo the last addition.

### Simplify Path (LC 71):

```java
public String simplifyPath(String path) {
    Deque<String> stack = new ArrayDeque<>();
    String[] parts = path.split("/");

    for (String part : parts) {
        if ("..".equals(part)) {
            if (!stack.isEmpty()) {
                stack.pop();
            }
        } else if (!".".equals(part) && !part.isEmpty()) {
            stack.push(part);
        }
    }

    StringBuilder sb = new StringBuilder();
    for (String dir : stack) {
        sb.insert(0, "/" + dir);
    }
    return sb.length() == 0 ? "/" : sb.toString();
}
```

### Remove Adjacent Duplicates (LC 1047):

```java
public String removeDuplicates(String s) {
    Deque<Character> stack = new ArrayDeque<>();
    for (char c : s.toCharArray()) {
        if (!stack.isEmpty() && stack.peek() == c) {
            stack.pop();
        } else {
            stack.push(c);
        }
    }
    StringBuilder sb = new StringBuilder();
    while (!stack.isEmpty()) {
        sb.append(stack.pop());
    }
    return sb.reverse().toString();
}
```

**🏷️ Problems:** LC 71 (Simplify Path), LC 1047 (Remove All Adjacent Duplicates), LC 844 (Backspace String Compare), LC 735 (Asteroid Collision).

---

## 🧭 Pattern 5: Design — Min Stack / Queue Using Stacks

**Recognition cues — reach for this when:**
- "Design a stack that supports getMin in O(1)"
- "Implement queue using stacks"
- "Design" + stack/queue in the title

### Min Stack (LC 155):

Two stacks: main stack + min stack (tracks the minimum at each state).

```java
class MinStack {
    private Deque<Integer> stack;
    private Deque<Integer> minStack;

    public MinStack() {
        stack = new ArrayDeque<>();
        minStack = new ArrayDeque<>();
    }

    public void push(int val) {
        stack.push(val);
        int min = minStack.isEmpty() ? val : Math.min(val, minStack.peek());
        minStack.push(min);
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

**🏷️ Problems:** LC 155 (Min Stack), LC 232 (Implement Queue using Stacks), LC 225 (Implement Stack using Queues).

---

## 🔬 Canonical Problem — LC 739: Daily Temperatures

> **Problem:** Given daily temperatures, return an array where `answer[i]` is the number of days you have to wait for a warmer temperature. If no warmer day, `answer[i] = 0`.

### Step 1 — Read and identify triggers

"For each element, find the **next greater** element and compute the **distance**. This is a textbook **Pattern 2: Monotonic Stack** problem."

### Step 2 — Choose the approach

"I'll maintain a stack of indices with unresolved temperatures. When I encounter a warmer temperature, I pop and compute the distance."

### Step 3 — Adapt and code

**Steps in plain English:**

1. **Initialize** — empty stack, result array of zeros.
2. **Walk left to right** — for each day:
3. **Pop resolved** — while stack top has a lower temperature, pop it; `answer[popped] = i - popped`.
4. **Push current** — this day is unresolved.

```java
public int[] dailyTemperatures(int[] temperatures) {
    int n = temperatures.length;
    int[] answer = new int[n];
    Deque<Integer> stack = new ArrayDeque<>();

    for (int i = 0; i < n; i++) {
        while (!stack.isEmpty() && temperatures[i] > temperatures[stack.peek()]) {
            int idx = stack.pop();
            answer[idx] = i - idx;
        }
        stack.push(i);
    }
    return answer;
}
```

### Step 4 — Verify with example

```
temps = [73, 74, 75, 71, 69, 72, 76, 73]

i=0 (73): stack=[]       → push 0.           stack=[0]
i=1 (74): 74 > 73        → pop 0, ans[0]=1.  push 1. stack=[1]
i=2 (75): 75 > 74        → pop 1, ans[1]=1.  push 2. stack=[2]
i=3 (71): 71 < 75        → push 3.           stack=[2,3]
i=4 (69): 69 < 71        → push 4.           stack=[2,3,4]
i=5 (72): 72 > 69        → pop 4, ans[4]=1.
          72 > 71        → pop 3, ans[3]=2.
          72 < 75        → push 5.           stack=[2,5]
i=6 (76): 76 > 72        → pop 5, ans[5]=1.
          76 > 75        → pop 2, ans[2]=4.
                         → push 6.           stack=[6]
i=7 (73): 73 < 76        → push 7.           stack=[6,7]

Remaining: ans[6]=0, ans[7]=0 (no warmer day)

Result: [1, 1, 4, 2, 1, 1, 0, 0] ✅
```

### Complexity

- **Time:** O(n) — each index is pushed and popped at most once
- **Space:** O(n) — stack can hold up to n indices

---

## ⚡ Problem Bank — Expanded

---

### LC 20: Valid Parentheses

> **Problem:** Given a string containing `()[]{}`, determine if brackets are valid (every open has a matching close in correct order). `"()[]{}"` → true, `"(]"` → false.

> **Approach:** Stack. Push the expected close bracket for each open. On close bracket, pop and compare.

```java
// Push the EXPECTED closing bracket — makes the pop comparison a single check
if (c == '(') stack.push(')');
else if (c == '{') stack.push('}');
else if (c == '[') stack.push(']');
// Close bracket: must match what we expect (stack top) or it's invalid
else if (stack.isEmpty() || stack.pop() != c) return false;
```

---

### LC 739: Daily Temperatures

> **Problem:** Given daily temperatures, for each day find how many days until a warmer temperature. `[73,74,75,71,69,72,76,73]` → `[1,1,4,2,1,1,0,0]`.

> **Approach:** Monotonic stack of indices. Pop when current temp > stack top's temp. Distance = `i - popped index`.

```java
// Pop all days colder than today — today is their "next warmer day"
while (!stack.isEmpty() && temps[i] > temps[stack.peek()]) {
    int idx = stack.pop();
    // Distance from that colder day to today
    answer[idx] = i - idx;
}
// Today is unresolved — push for future comparison
stack.push(i);
```

---

### LC 496: Next Greater Element I

> **Problem:** `nums1` is a subset of `nums2`. For each element in `nums1`, find the next greater element in `nums2`. `nums1=[4,1,2], nums2=[1,3,4,2]` → `[-1,3,-1]`.

> **Approach:** Build a `value → next greater` map from `nums2` using monotonic stack. Then look up each element in `nums1`.

```java
// Monotonic stack on nums2 builds: map(1→3, 3→4, 4→-1, 2→-1)
// For each x in nums1: result = map.getOrDefault(x, -1)
```

---

### LC 150: Evaluate Reverse Polish Notation

> **Problem:** Evaluate postfix expression. `["2","1","+","3","*"]` → `((2+1)*3)` = 9. Operators: `+ - * /`.

> **Approach:** Numbers push to stack. Operators pop two operands, compute, push result. Note: pop order matters for `-` and `/`.

```java
// Pop order matters: b is on top (second operand), a is below (first operand)
int b = stack.pop();
int a = stack.pop();
stack.push(a + b); // or -, *, /
```

---

### LC 394: Decode String

> **Problem:** Decode encoded string. `"3[a2[c]]"` → `"accaccacc"`. Number before `[` means repeat the enclosed content that many times.

> **Approach:** Two stacks: count stack + string stack. On `[` push both and reset. On `]` pop and repeat.

```java
// '[' — save current count and string, start a fresh inner group
if (c == '[') {
    countStack.push(k);
    strStack.push(current);
    current = new StringBuilder();
    k = 0;
}
// ']' — repeat inner group and append to the outer string
else if (c == ']') {
    int repeat = countStack.pop();
    StringBuilder prev = strStack.pop();
    for (int i = 0; i < repeat; i++) {
        prev.append(current);
    }
    current = prev;
}
```

---

### LC 155: Min Stack

> **Problem:** Design a stack that supports push, pop, top, and retrieving the minimum element — all in O(1) time.

> **Approach:** Two parallel stacks. Main stack stores values. Min stack stores the running minimum at each level.

```java
public void push(int val) {
    stack.push(val);
    // Min stack tracks the running minimum at each depth level
    minStack.push(minStack.isEmpty() ? val : Math.min(val, minStack.peek()));
}
```

---

### LC 84: Largest Rectangle in Histogram

> **Problem:** Given array of bar heights, find the area of the largest rectangle that fits under the histogram. `[2,1,5,6,2,3]` → 10 (5×2 rectangle at heights 5,6).

> **Approach:** Monotonic stack. Pop when current height is less than stack top. Width = distance between current index and new stack top.

```java
// Current bar is shorter — pop taller bars and compute their max rectangle
while (!stack.isEmpty() && heights[i] < heights[stack.peek()]) {
    int h = heights[stack.pop()];
    // Width extends from current index back to the new stack top (exclusive)
    int w = stack.isEmpty() ? i : i - stack.peek() - 1;
    area = Math.max(area, h * w);
}
```

---

### LC 71: Simplify Path

> **Problem:** Given a Unix file path, simplify it. `"/a/./b/../../c/"` → `"/c"`. `.` = current dir, `..` = parent dir.

> **Approach:** Stack as history. Split by `/`. Push directory names, pop on `..`, skip `.` and empty strings.

```java
// ".." means go up one directory — undo the last push
if ("..".equals(part)) {
    if (!stack.isEmpty()) {
        stack.pop();
    }
}
// Skip "." (current dir) and empty strings (double slashes); push real directory names
else if (!".".equals(part) && !part.isEmpty()) {
    stack.push(part);
}
```

---

### LC 1047: Remove All Adjacent Duplicates in String

> **Problem:** Repeatedly remove pairs of adjacent duplicate characters. `"abbaca"` → remove `bb` → `"aaca"` → remove `aa` → `"ca"`.

> **Approach:** Stack as history. If top matches current char → pop (cancel). Else push. Build result from remaining stack.

```java
// Top matches current char — cancel the pair (both destroyed)
if (!stack.isEmpty() && stack.peek() == c) {
    stack.pop();
}
// No match — this char survives (for now)
else {
    stack.push(c);
}
```

---

### LC 232: Implement Queue using Stacks

> **Problem:** Implement a FIFO queue using only two stacks. Support push, pop, peek, and empty.

> **Approach:** Two stacks: `input` and `output`. Push goes to input. Pop/peek checks output first; if empty, transfer ALL from input to output (reverses order → FIFO).

```java
public int pop() {
    // Lazy transfer: only move elements when output is empty (amortized O(1))
    if (output.isEmpty()) {
        // Reversing input→output converts LIFO order to FIFO order
        while (!input.isEmpty()) {
            output.push(input.pop());
        }
    }
    return output.pop();
}
```

---

### LC 1249: Minimum Remove to Make Valid Parentheses

> **Problem:** Remove the minimum number of parentheses to make the string valid. Example: `"lee(t(c)o)de)"` → `"lee(t(c)o)de"`.

> **Approach:** Stack stores indices of unmatched `(`. Walk string: on `)`, if stack empty → mark for removal, else pop (matched). After walk, stack holds unmatched `(` indices → also remove those.

```java
// Stack tracks indices of unmatched '(' — remove set collects indices to delete
Deque<Integer> stack = new ArrayDeque<>();
Set<Integer> remove = new HashSet<>();
for (int i = 0; i < s.length(); i++) {
    if (s.charAt(i) == '(') {
        stack.push(i);
    } else if (s.charAt(i) == ')') {
        // No matching '(' available — this ')' must be removed
        if (stack.isEmpty()) {
            remove.add(i);
        } else {
            stack.pop();
        }
    }
}
// Any '(' still on the stack were never matched — remove them too
while (!stack.isEmpty()) {
    remove.add(stack.pop());
}
```

---

### LC 32: Longest Valid Parentheses

> **Problem:** Find the length of the longest valid (well-formed) parentheses substring. Example: `"(()"` → `2`. `")()())"` → `4`.

> **Approach:** Stack stores indices. Push -1 as base. On `(`, push index. On `)`, pop — if stack empty push current index as new base, else `length = i - stack.peek()`.

```java
Deque<Integer> stack = new ArrayDeque<>();
// -1 acts as a "virtual base" — the boundary before the first valid sequence
stack.push(-1);
int maxLen = 0;
for (int i = 0; i < s.length(); i++) {
    if (s.charAt(i) == '(') {
        stack.push(i);
    } else {
        stack.pop();
        // Stack empty → this ')' is unmatched; use its index as the new base
        if (stack.isEmpty()) {
            stack.push(i);
        } else {
            // Valid run length = distance from current index to the boundary below
            maxLen = Math.max(maxLen, i - stack.peek());
        }
    }
}
```

---

### LC 503: Next Greater Element II (Circular)

> **Problem:** Given a circular array, find the next greater element for each element. Example: `[1,2,1]` → `[2,-1,2]` (after 1 at index 2, we wrap around to 2 at index 1).

> **Approach:** Same as LC 496/739 monotonic stack but iterate `2*n` times (simulate circular with `i % n`). Only assign results in first pass.

```java
int n = nums.length;
int[] result = new int[n];
// Default -1 means "no next greater exists"
Arrays.fill(result, -1);
Deque<Integer> stack = new ArrayDeque<>();
// Iterate 2*n to simulate wrapping around the circular array
for (int i = 0; i < 2 * n; i++) {
    // Resolve any stack elements smaller than the current value
    while (!stack.isEmpty() && nums[stack.peek()] < nums[i % n]) {
        result[stack.pop()] = nums[i % n];
    }
    // Only push indices from the first pass — second pass is just for resolving
    if (i < n) {
        stack.push(i);
    }
}
```

---

### LC 224: Basic Calculator

> **Problem:** Evaluate a string expression with `+`, `-`, `(`, `)`, and spaces. Example: `"(1+(4+5+2)-3)+(6+8)"` → `23`.

> **Approach:** Stack-based. Track `result` and `sign`. On `(`, push result and sign, reset. On `)`, pop and apply. Numbers can be multi-digit — parse digit by digit.

```java
// On '(': push result and sign, reset both
// On ')': result = stack.pop() * sign + result (apply saved sign) + stack.pop() (saved result)
```

---

### LC 844: Backspace String Compare

> **Problem:** Two strings with `#` as backspace. Return true if they're equal after processing. Example: `"ab#c", "ad#c"` → `true` (both become `"ac"`).

> **Approach:** Process from RIGHT with skip counters. When `#`, increment skip. When letter with skip > 0, skip it. Compare remaining chars. O(1) space.

```java
// Walk both strings backwards — O(1) space by counting backspaces instead of using a stack
int i = s.length() - 1, j = t.length() - 1;
int skipS = 0, skipT = 0;
while (i >= 0 || j >= 0) {
    // Skip characters deleted by '#' backspaces in s
    while (i >= 0 && (s.charAt(i) == '#' || skipS > 0)) {
        if (s.charAt(i) == '#') { skipS++; } else { skipS--; }
        i--;
    }
    // Skip characters deleted by '#' backspaces in t
    while (j >= 0 && (t.charAt(j) == '#' || skipT > 0)) {
        if (t.charAt(j) == '#') { skipT++; } else { skipT--; }
        j--;
    }
    // Compare the next surviving characters
    if (i >= 0 && j >= 0 && s.charAt(i) != t.charAt(j)) { return false; }
    // One string ran out but the other didn't — not equal
    if ((i >= 0) != (j >= 0)) { return false; }
    i--;
    j--;
}
```

---

### LC 735: Asteroid Collision

> **Problem:** Asteroids in a row. Positive = moving right, negative = moving left. When they collide, smaller one explodes. Equal = both explode. Return final state. Example: `[5,10,-5]` → `[5,10]`.

> **Approach:** Stack. For each asteroid: if positive, push. If negative, pop positive asteroids that are smaller. If equal, both destroyed. If stack empty or top is negative, push.

```java
Deque<Integer> stack = new ArrayDeque<>();
for (int a : asteroids) {
    boolean alive = true;
    // Collision only happens when top moves right (+) and current moves left (-)
    while (alive && !stack.isEmpty() && stack.peek() > 0 && a < 0) {
        // Top asteroid is smaller — it explodes, keep checking
        if (stack.peek() < -a) {
            stack.pop();
        }
        // Equal size — both explode
        else if (stack.peek() == -a) {
            stack.pop();
            alive = false;
        }
        // Top asteroid is bigger — current one explodes
        else {
            alive = false;
        }
    }
    if (alive) {
        stack.push(a);
    }
}
```

---

### LC 225: Implement Stack using Queues

> **Problem:** Implement a LIFO stack using only two queues. Support push, pop, top, empty.

> **Approach:** One-queue approach: on `push`, add to queue then rotate all previous elements to the back (so newest is at front).

```java
Queue<Integer> queue = new ArrayDeque<>();
public void push(int x) {
    queue.offer(x);
    // Rotate all previous elements behind the new one — newest ends up at front (LIFO)
    for (int i = 0; i < queue.size() - 1; i++) {
        queue.offer(queue.poll());
    }
}
public int pop() { return queue.poll(); }
```

---

## ⚠️ Interview Gotchas

### Edge cases interviewers will probe:

- **Empty stack pop** — always check `!stack.isEmpty()` before `pop()` or `peek()`
- **Unmatched brackets** — `"((("` → stack not empty at end → invalid
- **Single element** — monotonic stack: answer is -1 / 0
- **Nested brackets** — `"3[a2[c]]"` — the stack-based decode handles nesting naturally

### Follow-up questions:

| After solving... | They'll ask... | Answer |
| --- | --- | --- |
| Valid Parentheses | "What about multiple bracket types?" | Already handled — push expected close |
| Daily Temperatures | "Can you do it right-to-left?" | Yes, but left-to-right monotonic stack is cleaner |
| Next Greater Circular | "Array is circular?" | Iterate `2 * n`, use `i % n` for index |
| Min Stack | "Can you use one stack?" | Push `(val, min)` pairs — slightly less clean |

### Java stack trap:

**Use `Deque<> stack = new ArrayDeque<>()` not `Stack<>`.** The `Stack` class is legacy (synchronized, extends `Vector`). `ArrayDeque` is faster and the recommended implementation.

```java
// ❌ Legacy — slow due to synchronization
Stack<Integer> stack = new Stack<>();

// ✅ Modern — faster, recommended
Deque<Integer> stack = new ArrayDeque<>();
// push → stack.push()
// pop  → stack.pop()
// peek → stack.peek()
```

---

## 🧩 Speed Drill — 7 Minutes

**Part 1 — Pattern Recognition (2 minutes)**

1. "Check if parentheses are valid" → ___
2. "For each element, find the next greater" → ___
3. "Evaluate postfix expression" → ___
4. "Design stack with O(1) getMin" → ___
5. "Simplify Unix file path" → ___
6. "Decode string with nested brackets" → ___

**Answers:** 1. Bracket Matching, 2. Monotonic Stack, 3. Expression Eval, 4. Min Stack (parallel stack), 5. Stack as History, 6. Two-stack (count + string)

**Part 2 — Write the Template (3 minutes)**

From memory, write the Monotonic Stack template for Daily Temperatures. Include: stack initialization, while-pop loop, push, and what the stack stores (indices).

**Part 3 — The "Push Expected Close" Trick (2 minutes)**

For Valid Parentheses, write the 3 push lines and the else-if check line. Why is this better than pushing the open bracket?

**Answer:** Pushing the expected close means the pop comparison is ONE line (`stack.pop() != c`) instead of a mapping function. Fewer lines = fewer bugs.

**Scoring:** All correct = ready. Missed the monotonic stack direction = re-read Pattern 2.

---

## 🔗 Cross-References

| Topic | File |
| --- | --- |
| ArrayDeque + Queue syntax reference | `DSA/Reference/arraydeque-and-queue-reference.md` |
| BFS (queue-based) patterns | `DSA/Interview/trees-and-bfs-dfs.md` |
| String operations (for decode/path problems) | `DSA/Reference/string-operations-reference.md` |
| Java coding traps (Stack vs ArrayDeque) | `DSA/Implementation/java-coding-traps.md` |

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| May 2026 | **File created.** Interview Playbook for Stacks & Queues. 5 patterns: bracket matching, monotonic stack, expression eval, history/undo, design. Canonical walkthrough (LC 739), 10-problem bank, ArrayDeque vs Stack trap. |
