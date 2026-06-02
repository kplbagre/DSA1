# Two Pointers, Binary Search, Stacks, Heaps & Backtracking — Crash Course

> **Companion to:** `graph-dp-crash-course.md` (Graph + Tree + DP). Together these two files cover ALL high-frequency Salesforce SMTS R1 topics — 39 problems total. Same format: trigger → steps → key snippet → tweaks.

---

## 📅 Time Split

```
Two Pointers + Sliding Window:  40 min  (4 problems, ~10 min each)
Binary Search:                  25 min  (3 problems, ~8 min each)
Stacks:                         25 min  (3 problems, ~8 min each)
Heaps:                          25 min  (3 problems, ~8 min each)
Backtracking:                   25 min  (3 problems, ~8 min each)
─────────
~2.5 hours, 16 problems
```

---

# 🧭 TWO POINTERS & SLIDING WINDOW — 4 Must-Know Problems

---

## P1. LC 11 — Container With Most Water ⭐⭐

**Trigger:** "two lines", "most water", "maximize area"

**How to think:** Converging pointers. Start from widest container (lo=0, hi=n-1). Move the SHORTER side inward — only way to possibly find a taller line.

**Steps:**
1. lo = 0, hi = n-1 (widest possible container)
2. Area = min(height[lo], height[hi]) × (hi - lo)
3. Move the pointer with the SHORTER height inward
4. Update maxArea each time. Repeat until lo meets hi

**Key snippet:**

```java
int lo = 0, hi = height.length - 1, maxArea = 0;
while (lo < hi) {
    int area = Math.min(height[lo], height[hi]) * (hi - lo);
    maxArea = Math.max(maxArea, area);
    if (height[lo] < height[hi]) {
        lo++;   // short side limits area → move it
    } else {
        hi--;
    }
}
return maxArea;
```

**Same pattern:** LC 42 (Trapping Rain Water) — same two pointers but track leftMax/rightMax and add water at shorter side.

---

## P2. LC 15 — 3Sum ⭐⭐⭐ (Very likely at Salesforce)

**Trigger:** "three numbers sum to zero/target", "all triplets"

**How to think:** Sort + fix one number + converging two-pointer on the rest. The HARD part is skipping duplicates at all three positions.

**Steps:**
1. Sort the array
2. Outer loop fixes nums[i]. Skip duplicates: `if (i > 0 && nums[i] == nums[i-1]) continue`
3. Two pointers lo = i+1, hi = n-1. If sum < 0 → lo++. If sum > 0 → hi--
4. If sum == 0 → record triplet → skip duplicate lo and hi → move both inward

**Key snippet:**

```java
Arrays.sort(nums);
List<List<Integer>> result = new ArrayList<>();
for (int i = 0; i < nums.length - 2; i++) {
    if (i > 0 && nums[i] == nums[i - 1]) {
        continue;  // skip duplicate at position i
    }
    int lo = i + 1, hi = nums.length - 1;
    while (lo < hi) {
        int sum = nums[i] + nums[lo] + nums[hi];
        if (sum < 0) {
            lo++;
        } else if (sum > 0) {
            hi--;
        } else {
            result.add(List.of(nums[i], nums[lo], nums[hi]));
            while (lo < hi && nums[lo] == nums[lo + 1]) {
                lo++;  // skip duplicate lo
            }
            while (lo < hi && nums[hi] == nums[hi - 1]) {
                hi--;  // skip duplicate hi
            }
            lo++;
            hi--;
        }
    }
}
return result;
```

**Same pattern:** LC 16 (3Sum Closest) — track closest sum, no duplicate-skip needed. LC 18 (4Sum) — one more outer loop.

**Trap:** The THREE duplicate skips (i, lo, hi) are the #1 bug. Miss any one → duplicate triplets in output.

---

## P3. LC 3 — Longest Substring Without Repeating Characters ⭐⭐⭐

**Trigger:** "longest substring", "no repeating", "all distinct"

**How to think:** Variable sliding window. Expand right → if duplicate found → shrink left until no duplicate.

**Steps:**
1. HashMap tracks char → count
2. Expand right: add s[right] to map
3. While s[right] has count > 1 → remove s[left] from map, left++
4. Update maxLen = max(maxLen, right - left + 1)

**Key snippet:**

```java
Map<Character, Integer> count = new HashMap<>();
int left = 0, maxLen = 0;
for (int right = 0; right < s.length(); right++) {
    char c = s.charAt(right);
    count.merge(c, 1, Integer::sum);
    // Shrink until no duplicate
    while (count.get(c) > 1) {
        char lc = s.charAt(left);
        count.merge(lc, -1, Integer::sum);
        left++;
    }
    maxLen = Math.max(maxLen, right - left + 1);
}
return maxLen;
```

**Same pattern, small tweak:**

| Problem | Tweak |
| --- | --- |
| **LC 159 — At Most 2 Distinct** | Shrink when `map.size() > 2` instead of count > 1 |
| **LC 340 — At Most K Distinct** | Shrink when `map.size() > k` |
| **LC 424 — Longest Repeating Char Replacement** | Shrink when `windowSize - maxFreq > k` |

---

## P4. LC 76 — Minimum Window Substring ⭐⭐⭐

**Trigger:** "minimum window containing all characters", "smallest substring"

**How to think:** Variable window + need/have tracking. Expand right to satisfy, then shrink left to minimize.

**Steps:**
1. Build need[] from t. Count required unique chars
2. Expand right: have[c]++ → if have[c] == need[c] → formed++
3. While formed == required → update min window → shrink left → if have drops below need → formed--
4. Return min window substring

**Key snippet:**

```java
int[] need = new int[128], have = new int[128];
int required = 0;
for (char c : t.toCharArray()) {
    if (need[c] == 0) {
        required++;
    }
    need[c]++;
}
int formed = 0, left = 0, minLen = Integer.MAX_VALUE, start = 0;
for (int right = 0; right < s.length(); right++) {
    char c = s.charAt(right);
    have[c]++;
    if (have[c] == need[c]) {
        formed++;
    }
    while (formed == required) {
        if (right - left + 1 < minLen) {
            minLen = right - left + 1;
            start = left;
        }
        char lc = s.charAt(left);
        if (have[lc] == need[lc]) {
            formed--;
        }
        have[lc]--;
        left++;
    }
}
return minLen == Integer.MAX_VALUE ? "" : s.substring(start, start + minLen);
```

**Trap:** Use `int[128]` array (not HashMap) for speed. `have[c] == need[c]` (exact match, not >=) to count formed correctly.

---

# 🔍 BINARY SEARCH — 3 Must-Know Problems

---

## The Two Templates

```
Template 1: lo <= hi  → when searching for EXACT target → return mid
Template 2: lo < hi   → when searching for a BOUNDARY → return lo

Pick wrong template = infinite loop or off-by-one.
```

---

## B1. LC 33 — Search in Rotated Sorted Array ⭐⭐⭐

**Trigger:** "rotated sorted array", "search in rotated"

**How to think:** Standard binary search but ONE half is always sorted. Check which half → decide where target could be.

**Steps:**
1. lo = 0, hi = n-1, find mid
2. If nums[mid] == target → return mid
3. If LEFT half sorted (nums[lo] <= nums[mid]): target in [lo, mid)? → hi = mid-1. Else → lo = mid+1
4. Else RIGHT half sorted: target in (mid, hi]? → lo = mid+1. Else → hi = mid-1

**Key snippet:**

```java
int lo = 0, hi = nums.length - 1;
while (lo <= hi) {
    int mid = lo + (hi - lo) / 2;
    if (nums[mid] == target) {
        return mid;
    }
    if (nums[lo] <= nums[mid]) {
        // Left half is sorted
        if (target >= nums[lo] && target < nums[mid]) {
            hi = mid - 1;
        } else {
            lo = mid + 1;
        }
    } else {
        // Right half is sorted
        if (target > nums[mid] && target <= nums[hi]) {
            lo = mid + 1;
        } else {
            hi = mid - 1;
        }
    }
}
return -1;
```

**Same pattern:** LC 81 (Search in Rotated II — with duplicates) — when `nums[lo] == nums[mid]`, do `lo++` to skip.

**Trap:** `nums[lo] <= nums[mid]` — the `=` handles the 2-element case.

---

## B2. LC 153 — Find Minimum in Rotated Sorted Array ⭐⭐

**Trigger:** "find minimum in rotated"

**How to think:** Binary search on structure. Compare mid with hi. If `nums[mid] > nums[hi]` → min is right. Else → min is left or mid.

**Steps:**
1. lo = 0, hi = n-1
2. While lo < hi: if nums[mid] > nums[hi] → lo = mid + 1. Else → hi = mid
3. Return nums[lo]

**Key snippet:**

```java
int lo = 0, hi = nums.length - 1;
while (lo < hi) {
    int mid = lo + (hi - lo) / 2;
    if (nums[mid] > nums[hi]) {
        lo = mid + 1;  // min is in right half
    } else {
        hi = mid;       // min could BE mid
    }
}
return nums[lo];
```

**Trap:** `lo < hi` (not `<=`). And `hi = mid` (not `mid-1`) because mid might BE the minimum.

---

## B3. LC 875 — Koko Eating Bananas ⭐⭐ (Answer-space binary search)

**Trigger:** "minimum speed/capacity", "within H hours/days", "minimize the maximum"

**How to think:** Binary search on the ANSWER, not the input array. "What's the minimum speed?" → binary search between 1 and max(piles).

**Steps:**
1. lo = 1, hi = max(piles). Binary search on speed
2. For each candidate speed (mid): calculate total hours needed
3. If hours <= h → speed might work, try slower → hi = mid
4. If hours > h → too slow → lo = mid + 1
5. Return lo

**Key snippet:**

```java
int lo = 1, hi = 0;
for (int pile : piles) {
    hi = Math.max(hi, pile);
}
while (lo < hi) {
    int mid = lo + (hi - lo) / 2;
    int hours = 0;
    for (int pile : piles) {
        hours += (pile + mid - 1) / mid;  // ceil division
    }
    if (hours <= h) {
        hi = mid;       // can finish → try slower
    } else {
        lo = mid + 1;   // too slow → need faster
    }
}
return lo;
```

**Same pattern (binary search on answer):**

| Problem | Tweak |
| --- | --- |
| **LC 1011 — Ship Within D Days** | "Weight capacity" instead of speed. Sum until over capacity → new day |
| **LC 410 — Split Array Largest Sum** | Binary search on max-sum. Greedily split when sum exceeds mid |

**Trap:** `(pile + mid - 1) / mid` is ceiling division using integers — avoids floating-point issues.

---

# 📚 STACKS — 3 Must-Know Problems

---

## S1. LC 20 — Valid Parentheses ⭐⭐

**Trigger:** "valid parentheses", "balanced brackets"

**How to think:** Stack stores opening brackets. Closing bracket arrives → pop and check match.

**Steps:**
1. If opening bracket → push to stack
2. If closing bracket → if stack empty or top doesn't match → false
3. After all chars → stack must be empty

**Key snippet:**

```java
Deque<Character> stack = new ArrayDeque<>();
for (char c : s.toCharArray()) {
    if (c == '(') {
        stack.push(')');
    } else if (c == '{') {
        stack.push('}');
    } else if (c == '[') {
        stack.push(']');
    } else if (stack.isEmpty() || stack.pop() != c) {
        return false;
    }
}
return stack.isEmpty();
```

**Trick:** Push the EXPECTED closing bracket. Then `stack.pop() != c` is a direct char comparison — no mapping needed.

---

## S2. LC 739 — Daily Temperatures ⭐⭐⭐ (Monotonic stack — Salesforce worthy)

**Trigger:** "next greater", "next warmer", "days until"

**How to think:** Monotonic decreasing stack of INDICES. When current temp > top → pop (found answer for that day).

**Steps:**
1. Iterate left to right. Stack stores indices (not values)
2. While stack not empty AND temps[i] > temps[stack.peek()] → pop → answer[popped] = i - popped
3. Push current index onto stack

**Key snippet:**

```java
int[] answer = new int[n];
Deque<Integer> stack = new ArrayDeque<>();
for (int i = 0; i < n; i++) {
    while (!stack.isEmpty() && temps[i] > temps[stack.peek()]) {
        int prev = stack.pop();
        answer[prev] = i - prev;  // days until warmer
    }
    stack.push(i);
}
return answer;  // indices still in stack → answer stays 0 (no warmer day)
```

**Same pattern, small tweak:**

| Problem | Tweak |
| --- | --- |
| **LC 496 — Next Greater Element I** | Same stack. HashMap maps value → its next greater |
| **LC 84 — Largest Rectangle in Histogram** | Monotonic INCREASING stack. Pop when shorter bar → compute area = height × width |
| **LC 901 — Online Stock Span** | Monotonic decreasing. Count popped days = span |

---

## S3. LC 155 — Min Stack ⭐⭐

**Trigger:** "design stack", "getMin in O(1)"

**How to think:** Two stacks — main + min tracker. Min stack tracks running minimum.

**Steps:**
1. push(x): push to main. If x <= minStack.peek() (or empty) → push to minStack too
2. pop(): pop from main. If popped == minStack.peek() → pop minStack too
3. getMin(): return minStack.peek()

**Key snippet:**

```java
Deque<Integer> stack = new ArrayDeque<>();
Deque<Integer> minStack = new ArrayDeque<>();

void push(int val) {
    stack.push(val);
    if (minStack.isEmpty() || val <= minStack.peek()) {
        minStack.push(val);
    }
}

void pop() {
    if (stack.pop().equals(minStack.peek())) {
        minStack.pop();
    }
}

int getMin() {
    return minStack.peek();
}
```

**Trap:** Use `<=` (not `<`) when pushing to minStack. Duplicate mins must both be tracked. Also use `.equals()` for Integer comparison (not `==`) — autoboxing pitfall.

---

# ⚙️ HEAPS — 3 Must-Know Problems

---

## H1. LC 347 — Top K Frequent Elements ⭐⭐⭐

**Trigger:** "top k", "k most frequent"

**How to think:** Frequency map → min-heap of size k. Min-heap keeps k largest — smallest is on top (eviction candidate).

**Steps:**
1. Build frequency map: num → count
2. Min-heap of size k, ordered by frequency
3. Add each entry. If heap size > k → poll (evicts least frequent of the k)
4. Heap now contains top k elements

**Key snippet:**

```java
Map<Integer, Integer> freq = new HashMap<>();
for (int num : nums) {
    freq.merge(num, 1, Integer::sum);
}
// Min-heap by frequency — smallest freq on top (gets evicted)
PriorityQueue<Integer> heap = new PriorityQueue<>(
    (a, b) -> freq.get(a) - freq.get(b)
);
for (int key : freq.keySet()) {
    heap.offer(key);
    if (heap.size() > k) {
        heap.poll();  // evict least frequent
    }
}
int[] result = new int[k];
for (int i = 0; i < k; i++) {
    result[i] = heap.poll();
}
return result;
```

**Trap:** MIN-heap of size k (NOT max-heap of all elements). Min-heap evicts the smallest, leaving k largest. O(n log k).

**Same pattern:** LC 692 (Top K Frequent Words) — comparator adds alphabetical tiebreaker.

---

## H2. LC 23 — Merge K Sorted Lists ⭐⭐⭐

**Trigger:** "merge k sorted", "k sorted lists/arrays"

**How to think:** Min-heap of size k — one node from each list. Always poll smallest → advance that list → push next.

**Steps:**
1. Add head of each non-null list to min-heap (by node.val)
2. Poll smallest → append to result → if polled.next exists → push it
3. Repeat until heap empty

**Key snippet:**

```java
PriorityQueue<ListNode> heap = new PriorityQueue<>(
    (a, b) -> a.val - b.val
);
for (ListNode head : lists) {
    if (head != null) {
        heap.offer(head);
    }
}
ListNode dummy = new ListNode(0), curr = dummy;
while (!heap.isEmpty()) {
    ListNode node = heap.poll();
    curr.next = node;
    curr = curr.next;
    if (node.next != null) {
        heap.offer(node.next);  // advance that list
    }
}
return dummy.next;
```

**Same pattern:** LC 378 (Kth Smallest in Sorted Matrix) — heap of `(val, row, col)`, push right neighbor. LC 373 (K Smallest Pairs) — heap of index pairs.

---

## H3. LC 295 — Find Median from Data Stream ⭐⭐⭐

**Trigger:** "median", "data stream", "running median"

**How to think:** Two heaps — max-heap (lower half) + min-heap (upper half). Median lives at the tops.

**Steps:**
1. `lo` = max-heap (peek = largest of small half)
2. `hi` = min-heap (peek = smallest of large half)
3. addNum: add to lo → move lo's top to hi → if hi bigger → move hi's top back to lo
4. findMedian: equal sizes → average both tops. Else lo's top

**Key snippet:**

```java
// lo = max-heap (lower half),  hi = min-heap (upper half)
PriorityQueue<Integer> lo = new PriorityQueue<>(Collections.reverseOrder());
PriorityQueue<Integer> hi = new PriorityQueue<>();

void addNum(int num) {
    lo.offer(num);           // step 1: add to lower half
    hi.offer(lo.poll());     // step 2: move max-of-lower to upper
    if (hi.size() > lo.size()) {
        lo.offer(hi.poll()); // step 3: rebalance — lo stays same size or 1 bigger
    }
}

double findMedian() {
    if (lo.size() > hi.size()) {
        return lo.peek();
    }
    return (lo.peek() + hi.peek()) / 2.0;
}
```

**Trap:** The 3-step add (lo → hi → rebalance) always works. Don't try to be clever with if-else — it causes edge-case bugs.

---

# 🔄 BACKTRACKING — 3 Must-Know Problems

---

## The Key Rule

```
SUBSETS/COMBINATIONS: use start index → order doesn't matter → [1,2] and [2,1] are same
PERMUTATIONS:         use used[] array → order matters → [1,2] and [2,1] are different

Reuse allowed:  recurse with i      (same element again)
No reuse:       recurse with i + 1  (next element)
```

---

## BT1. LC 78 — Subsets ⭐⭐⭐ (The backtracking template)

**Trigger:** "all subsets", "power set", "all combinations"

**How to think:** Backtracking with start index. Every path state is a valid subset. Start index prevents duplicates.

**Steps:**
1. Add SNAPSHOT of current path to result (every state is valid)
2. For each position from `start` to end: add nums[i] to path
3. Recurse with start = i + 1 (no reuse)
4. Backtrack: remove last element

**Key snippet:**

```java
List<List<Integer>> result = new ArrayList<>();

void backtrack(int[] nums, int start, List<Integer> path) {
    result.add(new ArrayList<>(path));  // SNAPSHOT every state
    for (int i = start; i < nums.length; i++) {
        path.add(nums[i]);
        backtrack(nums, i + 1, path);   // i+1 = no reuse
        path.remove(path.size() - 1);   // undo
    }
}
// Call: backtrack(nums, 0, new ArrayList<>())
```

**Same pattern, small tweak:**

| Problem | Tweak |
| --- | --- |
| **LC 90 — Subsets II (duplicates)** | Sort first. Skip: `if (i > start && nums[i] == nums[i-1]) continue` |
| **LC 77 — Combinations (k elements)** | Only add to result when `path.size() == k` |

**Trap:** `new ArrayList<>(path)` — SNAPSHOT. Without it, all entries in result point to the same (eventually empty) list.

---

## BT2. LC 46 — Permutations ⭐⭐⭐

**Trigger:** "all permutations", "all orderings", "all arrangements"

**How to think:** Backtracking with used[] array. Every element can go at every position — used[] prevents reuse in the same permutation.

**Steps:**
1. If path.size() == nums.length → SNAPSHOT and return (complete permutation)
2. For EVERY element: if not used → mark used, add to path, recurse
3. Backtrack: unmark used, remove from path

**Key snippet:**

```java
void backtrack(int[] nums, boolean[] used, List<Integer> path) {
    if (path.size() == nums.length) {
        result.add(new ArrayList<>(path));  // complete → SNAPSHOT
        return;
    }
    for (int i = 0; i < nums.length; i++) {
        if (used[i]) {
            continue;
        }
        used[i] = true;
        path.add(nums[i]);
        backtrack(nums, used, path);
        path.remove(path.size() - 1);  // undo
        used[i] = false;
    }
}
```

**Same pattern:** LC 47 (Permutations II — with duplicates) — sort + `if (i > 0 && nums[i] == nums[i-1] && !used[i-1]) continue`.

---

## BT3. LC 39 — Combination Sum ⭐⭐

**Trigger:** "combinations that sum to target", "can reuse elements"

**How to think:** Same as Subsets but recurse with `i` (not `i+1`) for reuse. Only collect when sum == target.

**Steps:**
1. If remaining == 0 → SNAPSHOT (found valid combo). If remaining < 0 → return (prune)
2. For each candidate from `start`: add → recurse with same `i` (reuse) → backtrack
3. Sort candidates first so pruning (`break` when candidate > remaining) works

**Key snippet:**

```java
void backtrack(int[] candidates, int start, int remaining, List<Integer> path) {
    if (remaining == 0) {
        result.add(new ArrayList<>(path));
        return;
    }
    for (int i = start; i < candidates.length; i++) {
        if (candidates[i] > remaining) {
            break;  // prune — candidates sorted
        }
        path.add(candidates[i]);
        backtrack(candidates, i, remaining - candidates[i], path);  // i = reuse
        path.remove(path.size() - 1);
    }
}
// Pre-sort: Arrays.sort(candidates)
```

**Same pattern:**

| Variant | Recurse with | Extra |
| --- | --- | --- |
| **LC 39 — Combo Sum (reuse)** | `i` | — |
| **LC 40 — Combo Sum II (no reuse + dupes)** | `i + 1` | Sort + `if (i > start && nums[i] == nums[i-1]) continue` |

---

## ⚡ Quick Decision Tree — In the Interview

```
TWO POINTERS — What variant?
│
├── Sorted + "two/three sum"            → Converging pointers (P1, P2)
├── "Longest substring" + constraint    → Variable sliding window (P3)
├── "Minimum window containing X"       → Window + need/have tracking (P4)
└── "Container / trapping water"        → Converging, move shorter side (P1)

BINARY SEARCH — What to search?
│
├── "Rotated sorted + find target"      → Check which half sorted (B1)
├── "Rotated sorted + find min"         → Compare mid with hi (B2)
└── "Minimum speed / capacity / max"    → Answer-space binary search (B3)

STACKS — What pattern?
│
├── "Valid parentheses / brackets"      → Push expected closing (S1)
├── "Next greater / warmer / span"      → Monotonic stack of indices (S2)
└── "Design stack + getMin O(1)"        → Two stacks: main + min tracker (S3)

HEAPS — What pattern?
│
├── "Top K / K most frequent"           → Min-heap of size K (H1)
├── "Merge K sorted lists/arrays"       → Min-heap of K heads (H2)
└── "Median from stream"                → Two heaps: max-lo + min-hi (H3)

BACKTRACKING — Subsets or Permutations?
│
├── "All subsets / combinations"        → start index + i+1 (BT1)
├── "All permutations / orderings"      → used[] array (BT2)
└── "Combo sum / can reuse elements"    → start index + i (BT3)
```

---

## 🔗 Cross-References

| Need more? | File |
| --- | --- |
| Graph + Tree + DP crash course | `DSA/Interview/graph-dp-crash-course.md` |
| Full two-pointer templates | `DSA/Interview/two-pointers-and-sliding-window.md` |
| Full binary search templates | `DSA/Interview/binary-search.md` |
| Full stacks templates | `DSA/Interview/stacks-and-queues.md` |
| Full heaps templates | `DSA/Interview/heaps.md` |
| Full backtracking templates | `DSA/Interview/backtracking.md` |
| Pre-submit bug checklist | `DSA/Interview/common-bugs-checklist.md` |

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| June 2026 | **File created.** Companion crash course — 16 problems across Two Pointers, Binary Search, Stacks, Heaps, Backtracking. Together with `graph-dp-crash-course.md` covers all Salesforce SMTS R1 high-frequency topics (39 problems total). |
