# Heaps & Priority Queues — Interview Playbook

> **Read this file when:** You have an interview in 1-2 days and need to recognize heap patterns. A heap is the answer whenever you see "top K", "Kth largest", or "merge K sorted." This file teaches you to spot those patterns and write the templates cold.

---

## 🎯 Why You're Reading This

Heap problems appear constantly in FAANG interviews because they test whether you know when NOT to sort. Sorting gives you all elements in order (O(n log n)); a heap gives you just the top K (O(n log k)) — and interviewers want to see that you know the difference.

After reading this file, you should be able to:
1. Recognize the 5 heap patterns from problem wording alone
2. Know when to use a min-heap vs max-heap (it's counterintuitive for top-K)
3. Write the `PriorityQueue` templates from memory

---

## 🔧 Essential Methods — Know These Cold

| Method | What it does | Used in |
| --- | --- | --- |
| `new PriorityQueue<>()` | Min-heap (smallest element at top — Java default) | All patterns |
| `new PriorityQueue<>(Collections.reverseOrder())` | Max-heap (largest element at top) | Pattern 4 |
| `new PriorityQueue<>((a, b) -> a[0] - b[0])` | Custom comparator (sort by first element) | Pattern 3 |
| `pq.offer(element)` | Add element — O(log n) | All patterns |
| `pq.poll()` | Remove and return top (smallest/largest) — O(log n) | All patterns |
| `pq.peek()` | View top without removing — O(1) | Patterns 2, 4 |
| `pq.size()` | Current number of elements — O(1) | Patterns 1, 2, 4 |
| `pq.isEmpty()` | Check if empty — O(1) | Pattern 3 |

> **Full reference:** `../Reference/arraydeque-and-queue-reference.md`

### 🔄 Lambda & Shorthand Explanations with Fallbacks

**1. `new PriorityQueue<>((a, b) -> expression)` — Custom comparator heap**

```java
// What it does:
//   (a, b) -> expression is a Comparator lambda
//   Return NEGATIVE → a comes first (a is "smaller" / higher priority)
//   Return POSITIVE → b comes first (b is "smaller" / higher priority)
//   Return ZERO     → equal priority
//
// Example: min-heap by frequency (lower freq = higher priority = evict first)
PriorityQueue<Integer> pq = new PriorityQueue<>((a, b) -> freq.get(a) - freq.get(b));

// 🔄 Fallback — sort a list instead of using a heap:
List<Integer> sorted = new ArrayList<>(freq.keySet());
Collections.sort(sorted, (a, b) -> freq.get(b) - freq.get(a));  // descending
// Then take the first K elements
```

**2. `Collections.reverseOrder()` — Max-heap shorthand**

```java
// What it does:
//   Java's PriorityQueue is a MIN-heap by default (smallest at top)
//   Collections.reverseOrder() flips it to a MAX-heap (largest at top)
PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Collections.reverseOrder());

// 🔄 Fallback — use explicit comparator:
PriorityQueue<Integer> maxHeap = new PriorityQueue<>((a, b) -> b - a);
// b - a means: larger values get higher priority (come out first)
```

**3. `freq.merge(n, 1, Integer::sum)` — Increment a counter**

```java
// merge: if key absent → put(key, 1); if present → put(key, old + 1)
// Integer::sum is shorthand for (oldVal, newVal) -> oldVal + newVal
freq.merge(n, 1, Integer::sum);

// 🔄 Fallback:
freq.put(n, freq.getOrDefault(n, 0) + 1);
```

**4. `Integer.compare(a, b)` — Overflow-safe comparison**

```java
// What it does: compares two ints safely (no overflow risk)
//   a < b → returns -1
//   a == b → returns 0
//   a > b → returns 1
// ALWAYS prefer over a - b when values could be Integer.MIN_VALUE/MAX_VALUE
PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> Integer.compare(a[0], b[0]));

// 🔄 Fallback — a[0] - b[0] works when values are bounded (no overflow):
PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> a[0] - b[0]);
```

---

## 🧠 The Mental Model — When to Think "Heap"

A heap is the right tool when you need **partial ordering** — you don't need ALL elements sorted, just quick access to the extreme (min or max). Ask yourself:

```
"Do I need the top K / Kth element / ongoing min-max?"
│
├── YES → Use a heap
│   │
│   ├── "Top K largest" or "K most frequent"
│   │   └── Pattern 1: Min-heap of size K ⭐
│   │       (smallest of the K largest is at top — easy to evict)
│   │
│   ├── "Kth largest element" (single answer)
│   │   └── Pattern 2: Min-heap of size K, peek = answer ⭐
│   │
│   ├── "Merge K sorted lists/arrays"
│   │   └── Pattern 3: Min-heap of K heads ⭐
│   │
│   ├── "Running median" or "balance two halves"
│   │   └── Pattern 4: Two Heaps (max-heap + min-heap)
│   │
│   └── "Schedule tasks with cooldown" or "greedy + next available"
│       └── Pattern 5: Greedy + Heap
│
└── NO → Probably sorting or a different data structure
```

### 🎨 Visual — Why Min-Heap for Top-K Largest (Counterintuitive)

```
Goal: find top 3 largest from [5, 1, 8, 3, 9, 2, 7]

❌ WRONG intuition: "I want the LARGEST, so use a MAX-heap"
   → Max-heap of all n elements = O(n log n) = just sorting. No benefit.

✅ RIGHT approach: "Keep a MIN-heap of size K=3"

   Process: 5 → [5]       (size < 3, just add)
            1 → [1,5]     (size < 3, just add)
            8 → [1,5,8]   (size = 3, heap is full)
            3 → peek=1, 3>1 → poll 1, add 3 → [3,5,8]
            9 → peek=3, 9>3 → poll 3, add 9 → [5,8,9]
            2 → peek=5, 2<5 → skip (too small for top 3)
            7 → peek=5, 7>5 → poll 5, add 7 → [7,8,9]

   Min-heap of size 3:
   ┌───┐
   │ 7 │  ← peek = smallest of top-3 = "gatekeeper"
   ├───┤     anything smaller than 7 gets rejected
   │ 8 │
   ├───┤
   │ 9 │
   └───┘

KEY INVARIANT:
   The min-heap acts as a "bouncer" — peek() is the weakest of the top K.
   Any new element must beat the bouncer to get in.
   Result: heap always contains exactly the K largest elements.
```

---

## 🧭 Pattern 1: Top-K Elements ⭐

**What this solves:** You need the K most important elements (largest, most frequent, closest) from N total elements. The goal is to find exactly K elements without sorting everything.

**Recognition cues — reach for this when:**
- "K most frequent elements"
- "K largest / K smallest elements" (return all K, not just the Kth)
- "Top K [anything]" where K << n

**Brute force:** Sort all N elements by the ranking criterion, then take the first K. O(n log n) time, O(n) space. Works but wastes computation ranking elements you'll never use.

**Key insight:** You only need to track the K "winners." A min-heap of size K acts as a bouncer — anything weaker than the current weakest winner is rejected immediately in O(log k). No need to sort all N.

**Steps in plain English:**

1. **Build a frequency map** (if needed) — count occurrences of each element.
2. **Create a min-heap of size K** — for top-K largest, use min-heap (counterintuitive!).
3. **Iterate through elements** — if heap size < K, add. If current > peek(), poll and add.
4. **Heap contains the answer** — drain the heap into the result.

```java
public int[] topKFrequent(int[] nums, int k) {
    // Step 1 — count frequencies
    Map<Integer, Integer> freq = new HashMap<>();
    for (int n : nums) {
        // merge: if absent → put(n, 1); if present → put(n, old + 1)
        freq.merge(n, 1, Integer::sum);
        // 🔄 Fallback: freq.put(n, freq.getOrDefault(n, 0) + 1);
    }
    // Step 2 — min-heap ordered by frequency
    // (a, b) -> freq.get(a) - freq.get(b): lower freq = higher priority (evicted first)
    PriorityQueue<Integer> pq = new PriorityQueue<>(
        (a, b) -> freq.get(a) - freq.get(b)
    );
    // 🔄 Fallback: sort a list by freq descending, take first K
    // Step 3 — maintain heap of size K
    for (int num : freq.keySet()) {
        pq.offer(num);
        if (pq.size() > k) {
            pq.poll();
        }
    }
    // Step 4 — drain heap
    int[] result = new int[k];
    for (int i = 0; i < k; i++) {
        result[i] = pq.poll();
    }
    return result;
}
```

**Complexity (optimal):** O(n log k) time, O(k) space — n elements each do one O(log k) heap operation

**🏷️ Problems:** LC 347 (Top K Frequent Elements), LC 692 (Top K Frequent Words), LC 973 (K Closest Points to Origin).

---

## 🧭 Pattern 2: Kth Largest / Kth Smallest ⭐

**What this solves:** Find the single element that would sit at position K if the array were sorted. You need one value, not all K — so sorting everything is overkill.

**Recognition cues — reach for this when:**
- "Find the Kth largest element"
- "Find the Kth smallest element"
- A stream of numbers and you need the Kth largest at any point

**Brute force:** Sort the array, return the element at index `n - k`. O(n log n) time, O(1) space (in-place sort). Correct but sorts elements you never needed ranked.

**Key insight:** After processing all elements through a size-K min-heap, the heap holds exactly the K largest elements. The top (`peek()`) is the smallest of those K — which is the Kth largest overall. No sorting needed.

**Steps in plain English:**

1. **Create a min-heap of size K** — for Kth largest.
2. **Add elements** — if heap size < K, add. If current > peek(), poll and add.
3. **Answer = peek()** — the top of a size-K min-heap IS the Kth largest.

```java
public int findKthLargest(int[] nums, int k) {
    PriorityQueue<Integer> pq = new PriorityQueue<>();
    for (int num : nums) {
        pq.offer(num);
        if (pq.size() > k) {
            pq.poll();
        }
    }
    return pq.peek();
}
```

**Complexity (optimal):** O(n log k) time, O(k) space

**🏷️ Problems:** LC 215 (Kth Largest Element in an Array), LC 703 (Kth Largest Element in a Stream).

---

## 🧭 Pattern 3: Merge K Sorted Lists/Arrays ⭐

**What this solves:** Combine K already-sorted sequences into one globally sorted sequence. Concatenating and re-sorting throws away the pre-sorted structure of each list.

**Recognition cues — reach for this when:**
- "Merge K sorted lists into one sorted list"
- "Smallest range covering elements from K lists"
- Multiple sorted sources need to be combined

**Brute force:** Collect all elements from all K lists into one array, sort it, rebuild the result. O(n log n) time where n = total elements. Ignores the pre-sorted structure entirely.

**Key insight:** The globally smallest unprocessed element must be the current head of one of the K lists. A min-heap of K heads finds it in O(log K) instead of checking all K heads one by one.

**Steps in plain English:**

1. **Create a min-heap** — seed it with the head of each sorted list (K elements).
2. **Poll the smallest** — add it to the result.
3. **Push the next element from that same list** — the list that just contributed its head now contributes its next element.
4. **Repeat until heap is empty.**

```java
public ListNode mergeKLists(ListNode[] lists) {
    // (a, b) -> a.val - b.val: min-heap by node value (smallest val polled first)
    PriorityQueue<ListNode> pq = new PriorityQueue<>(
        (a, b) -> a.val - b.val
    );
    // 🔄 Fallback: new PriorityQueue<>((a, b) -> Integer.compare(a.val, b.val))
    // Step 1 — seed with heads
    for (ListNode head : lists) {
        if (head != null) {
            pq.offer(head);
        }
    }
    ListNode dummy = new ListNode(0);
    ListNode tail = dummy;
    // Steps 2-4 — poll smallest, push next
    while (!pq.isEmpty()) {
        ListNode smallest = pq.poll();
        tail.next = smallest;
        tail = tail.next;
        if (smallest.next != null) {
            pq.offer(smallest.next);
        }
    }
    return dummy.next;
}
```

### 🎨 Visual — Merge K Sorted Lists

```
List 1: 1 → 4 → 7
List 2: 2 → 5 → 8
List 3: 3 → 6 → 9

Min-heap (size K=3, always holds one node from each list):

Step 1: Heap = [1, 2, 3]     → poll 1, push 4 from List 1
Step 2: Heap = [2, 3, 4]     → poll 2, push 5 from List 2
Step 3: Heap = [3, 4, 5]     → poll 3, push 6 from List 3
Step 4: Heap = [4, 5, 6]     → poll 4, push 7 from List 1
...

Result: 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9

KEY INVARIANT:
   The heap always contains at most K elements (one from each list).
   Polling gives the globally smallest unprocessed element.
   Time: O(n log K) where n = total elements across all lists.
```

**Complexity (optimal):** O(n log K) time, O(K) space — n = total elements, K = number of lists

**🏷️ Problems:** LC 23 (Merge K Sorted Lists), LC 378 (Kth Smallest Element in a Sorted Matrix).

---

## 🧭 Pattern 4: Two Heaps (Running Median)

**What this solves:** Maintain a running median as elements arrive one at a time. Re-sorting after every insertion is too slow; you need O(log n) insertion and O(1) median retrieval.

**Recognition cues — reach for this when:**
- "Find median from data stream"
- "Balance two halves" — need quick access to both the max of the lower half and min of the upper half
- Running statistics that split the data into two groups

**Brute force:** Keep all seen elements in a list, sort it after each insertion, return the middle element. O(n) per insertion for sorting → O(n²) for n insertions total.

**Key insight:** Split elements into two halves — lower half in a max-heap, upper half in a min-heap. The two tops are always adjacent to the median. Keep them balanced (sizes differ by ≤ 1) and the median is O(1) to compute.

**Steps in plain English:**

1. **Two heaps** — `maxHeap` holds the smaller half, `minHeap` holds the larger half.
2. **On add** — add to `maxHeap` first, then rebalance: move `maxHeap.peek()` to `minHeap` if needed, and ensure sizes differ by at most 1.
3. **On findMedian** — if sizes equal, average of both peeks. If one is bigger, its peek is the median.

```java
class MedianFinder {
    // maxHeap = smaller half (largest of the small half at top)
    // Collections.reverseOrder() flips default min-heap → max-heap
    PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Collections.reverseOrder());
    // 🔄 Fallback: new PriorityQueue<>((a, b) -> b - a)
    // minHeap = larger half (smallest of the large half at top) — default min-heap
    PriorityQueue<Integer> minHeap = new PriorityQueue<>();

    public void addNum(int num) {
        maxHeap.offer(num);
        // Ensure maxHeap's top ≤ minHeap's top
        minHeap.offer(maxHeap.poll());
        // Rebalance: maxHeap should be same size or 1 bigger
        if (minHeap.size() > maxHeap.size()) {
            maxHeap.offer(minHeap.poll());
        }
    }

    public double findMedian() {
        if (maxHeap.size() > minHeap.size()) {
            return maxHeap.peek();
        }
        return (maxHeap.peek() + minHeap.peek()) / 2.0;
    }
}
```

### 🎨 Visual — Two Heaps for Running Median

```
Data stream: 5, 15, 1, 3

After adding 5:    maxHeap=[5]     minHeap=[]       median=5
After adding 15:   maxHeap=[5]     minHeap=[15]     median=10
After adding 1:    maxHeap=[1,5]   minHeap=[15]     median=5
                   (wait — maxHeap has [5,1], top=5)
After adding 3:    maxHeap=[3,1]   minHeap=[5,15]   median=(3+5)/2=4

   maxHeap (max at top)    minHeap (min at top)
   ┌───┐                   ┌───┐
   │ 3 │ ← max of left     │ 5 │ ← min of right
   ├───┤                   ├───┤
   │ 1 │                   │15 │
   └───┘                   └───┘
   smaller half             larger half

KEY INVARIANT:
   maxHeap.peek() ≤ minHeap.peek() (left half ≤ right half)
   |maxHeap.size() - minHeap.size()| ≤ 1 (balanced)
   Median is always accessible from the tops in O(1).
```

**Complexity (optimal):** O(log n) per `addNum`, O(1) per `findMedian`, O(n) space

**🏷️ Problems:** LC 295 (Find Median from Data Stream), LC 480 (Sliding Window Median).

---

## 🧭 Pattern 5: Greedy + Heap (Scheduling/Cooldown)

**What this solves:** You must process tasks/characters in some order subject to constraints (no two adjacent same, cooldown between repeats). The optimal order always depends on picking the highest-priority available item next.

**Recognition cues — reach for this when:**
- "Schedule tasks with cooldown between same tasks"
- "Reorganize string so no two adjacent are the same"
- "Process items greedily, always picking the highest priority available"

**Brute force:** Try all valid orderings of tasks, pick the one with minimum total time (or maximum length). Exponential — factorial time for n distinct tasks.

**Key insight:** Always scheduling the most frequent available task minimizes wasted idle time. A max-heap by frequency gives the most frequent task in O(log n) each round without re-sorting after every pick.

**Steps in plain English:**

1. **Build frequency map** — count how many times each task/char appears.
2. **Add all to max-heap** — ordered by frequency (most frequent = highest priority).
3. **Greedily pick** — each round, poll from heap, process, decrement count, put back if count > 0.
4. **Handle cooldown** — use a queue/wait list to hold tasks that are "cooling down."

```java
public int leastInterval(char[] tasks, int n) {
    int[] freq = new int[26];
    for (char c : tasks) {
        freq[c - 'A']++;
    }
    // Max-heap by frequency — reverseOrder() flips min-heap → max-heap
    PriorityQueue<Integer> pq = new PriorityQueue<>(Collections.reverseOrder());
    // 🔄 Fallback: new PriorityQueue<>((a, b) -> b - a)
    for (int f : freq) {
        if (f > 0) {
            pq.offer(f);
        }
    }
    int time = 0;
    while (!pq.isEmpty()) {
        List<Integer> temp = new ArrayList<>();
        // Process up to n+1 tasks in one cooldown cycle
        for (int i = 0; i <= n; i++) {
            if (!pq.isEmpty()) {
                int count = pq.poll() - 1;
                if (count > 0) {
                    temp.add(count);
                }
            }
            time++;
            // If both heap and temp are empty, we're done
            if (pq.isEmpty() && temp.isEmpty()) {
                break;
            }
        }
        // Put remaining tasks back
        for (int count : temp) {
            pq.offer(count);
        }
    }
    return time;
}
```

**Complexity (optimal):** O(n log n) time, O(n) space — n = total task count, heap operations per cycle are O(log n)

**🏷️ Problems:** LC 621 (Task Scheduler), LC 767 (Reorganize String), LC 1405 (Longest Happy String).

---

## 🔬 Canonical Problem — LC 215: Kth Largest Element in an Array

> **Problem:** Given an integer array and an integer k, return the kth largest element. Note that it is the kth largest, not the kth distinct element. Example: `nums = [3,2,1,5,6,4], k = 2` → `5`.

### Step 1 — Read and identify triggers

"Kth largest element." This directly triggers **Pattern 2: Kth Largest/Smallest**. A min-heap of size K gives the answer at `peek()`.

### Step 2 — Choose the template

Min-heap of size K. As I iterate:
- If heap has fewer than K elements, just add.
- If current element > peek(), it belongs in the top K — poll the smallest and add current.
- After processing all elements, peek() = Kth largest.

### Step 3 — Adapt and code

**Steps in plain English:**

1. **Create min-heap** — default `PriorityQueue` in Java.
2. **Iterate all elements** — offer each, then if size > K, poll (evicts the smallest).
3. **Answer = peek()** — the smallest element in the top-K set.

```java
class Solution {
    public int findKthLargest(int[] nums, int k) {
        PriorityQueue<Integer> pq = new PriorityQueue<>();
        for (int num : nums) {
            pq.offer(num);
            if (pq.size() > k) {
                pq.poll();
            }
        }
        return pq.peek();
    }
}
```

### Step 4 — Verify with example

`nums = [3,2,1,5,6,4], k = 2`:
- Add 3: [3], size 1 ≤ 2
- Add 2: [2,3], size 2 ≤ 2
- Add 1: [1,2,3], size 3 > 2 → poll 1 → [2,3]
- Add 5: [2,3,5], size 3 > 2 → poll 2 → [3,5]
- Add 6: [3,5,6], size 3 > 2 → poll 3 → [5,6]
- Add 4: [4,5,6], size 3 > 2 → poll 4 → [5,6]
- peek() = **5** ✅

### Complexity

- **Time:** O(n log k) — each of n elements does a log-k heap operation
- **Space:** O(k) — heap holds at most k elements

---

## ⚡ Problem Bank — Key Twists

---

### LC 347: Top K Frequent Elements

> **Problem:** Given an integer array and an integer k, return the k most frequent elements (in any order). Example: `nums = [1,1,1,2,2,3], k = 2` → `[1,2]`.

> **Brute force:** Build frequency map, sort all unique elements by frequency, take the top K. O(n log n) time, O(n) space.
> **Key insight:** Min-heap of size K ordered by frequency — evict the least frequent when size exceeds K. Only K elements in the heap at any time.
> **Approach:** Build frequency map, then min-heap of size K ordered by frequency. Alternative: bucket sort by frequency for O(n).

```java
Map<Integer, Integer> freq = new HashMap<>();
// merge: increment count. Integer::sum = (old, 1) -> old + 1
for (int n : nums) freq.merge(n, 1, Integer::sum);
// 🔄 Fallback: freq.put(n, freq.getOrDefault(n, 0) + 1);
// (a, b) -> freq.get(a) - freq.get(b): min-heap by frequency (lowest freq evicted first)
PriorityQueue<Integer> pq = new PriorityQueue<>((a, b) -> freq.get(a) - freq.get(b));
// 🔄 Fallback: sort list by freq descending, take first K
for (int num : freq.keySet()) {
    pq.offer(num);
    // Evict the least-frequent element once heap exceeds size K
    if (pq.size() > k) pq.poll();
}
```

**Complexity (optimal):** O(n log k) time, O(n + k) space — n for freq map, k for heap

---

### LC 23: Merge K Sorted Lists

> **Problem:** Merge K sorted linked lists into one sorted linked list. Example: `lists = [[1,4,5],[1,3,4],[2,6]]` → `[1,1,2,3,4,4,5,6]`.

> **Brute force:** Collect all nodes into an array, sort by value, rebuild the linked list. O(n log n) time where n = total nodes. Ignores pre-sorted structure of each list.
> **Key insight:** The next globally smallest node must be the current head of one of the K lists. A min-heap of K heads finds it in O(log K) per step.
> **Approach:** Pattern 3 — seed min-heap with K heads. Poll smallest, push that node's `.next`. Use dummy node for result.

```java
// (a, b) -> a.val - b.val: min-heap by node value
PriorityQueue<ListNode> pq = new PriorityQueue<>((a, b) -> a.val - b.val);
// 🔄 Fallback: new PriorityQueue<>((a, b) -> Integer.compare(a.val, b.val))
// Seed heap with the head node of each list
for (ListNode head : lists) if (head != null) pq.offer(head);
while (!pq.isEmpty()) {
    // Poll the globally smallest node across all K lists
    ListNode node = pq.poll();
    tail.next = node;
    tail = tail.next;
    // Advance that list's pointer — push its next node into the heap
    if (node.next != null) pq.offer(node.next);
}
```

**Complexity (optimal):** O(n log K) time, O(K) space — n = total nodes, K = number of lists

---

### LC 973: K Closest Points to Origin

> **Problem:** Given an array of points and an integer K, return the K closest points to the origin `(0,0)`. Distance = `x² + y²` (no need for sqrt). Example: `points = [[1,3],[-2,2]], K = 1` → `[[-2,2]]`.

> **Brute force:** Compute all distances, sort points by distance, return first K. O(n log n) time, O(n) space.
> **Key insight:** Max-heap of size K by distance — the farthest point in the heap is the weakest candidate. Any new point closer than the farthest evicts it.
> **Approach:** Max-heap of size K ordered by distance. If new point is closer than the farthest in the heap, swap. Avoids sorting all N points.

```java
// MAX-heap by distance: farthest point at top (gets evicted when heap > K)
// b_dist - a_dist → larger distance = higher priority = polled first
PriorityQueue<int[]> pq = new PriorityQueue<>(
    (a, b) -> (b[0]*b[0] + b[1]*b[1]) - (a[0]*a[0] + a[1]*a[1])
);
// 🔄 Fallback: sort points by distance, take first K
// Arrays.sort(points, (a, b) -> (a[0]*a[0]+a[1]*a[1]) - (b[0]*b[0]+b[1]*b[1]));
for (int[] p : points) {
    pq.offer(p);
    if (pq.size() > k) pq.poll();
}
```

**Complexity (optimal):** O(n log k) time, O(k) space

---

### LC 295: Find Median from Data Stream

> **Problem:** Design a data structure that supports `addNum(int num)` and `findMedian()` in a stream of integers. Example: add 1, add 2 → median 1.5; add 3 → median 2.

> **Brute force:** Keep all seen elements in a sorted list, insert at the right position each time, return the middle element. O(n) per insertion → O(n²) total.
> **Key insight:** Two heaps split the data into lower and upper halves. Their tops are always adjacent to the median — O(1) to compute after each O(log n) insertion.
> **Approach:** Pattern 4 — two heaps. Max-heap for left half, min-heap for right half. Rebalance after each add so sizes differ by at most 1.

```java
// Add: always go maxHeap first → balance to minHeap → rebalance sizes
maxHeap.offer(num);
// Move maxHeap's largest to minHeap so all left-half values stay <= right-half
minHeap.offer(maxHeap.poll());
// Keep maxHeap same size or 1 bigger — it holds the median when count is odd
if (minHeap.size() > maxHeap.size()) maxHeap.offer(minHeap.poll());
// Median: both peeks or the bigger heap's peek
```

**Complexity (optimal):** O(log n) per `addNum`, O(1) per `findMedian`, O(n) space

---

### LC 621: Task Scheduler

> **Problem:** Given tasks and a cooldown period `n`, find the minimum intervals (including idle) to complete all tasks. Same task must have at least `n` intervals between executions. Example: `tasks = ["A","A","A","B","B","B"], n = 2` → `8` (A B idle A B idle A B).

> **Brute force:** Try all valid task orderings, find the one with minimum total time. Factorial time — impractical.
> **Key insight:** Always scheduling the most frequent remaining task minimizes forced idle time. Max-heap gives the most frequent task in O(log n) without re-sorting after each pick.
> **Approach:** Pattern 5 — max-heap by frequency. Each cycle processes up to `n+1` distinct tasks. If fewer available, idle slots fill the gap.

```java
// Each cycle: process n+1 tasks, put decremented counts in temp list
// After cycle: put temp back into heap
// Time increments every step (task or idle)
```

**Complexity (optimal):** O(n log n) time, O(n) space

---

### LC 703: Kth Largest Element in a Stream

> **Problem:** Design a class that finds the kth largest element in a stream. `add(val)` returns the kth largest after adding `val`. Example: `k=3, init=[4,5,8,2]`, `add(3)→4`, `add(5)→5`.

> **Brute force:** Keep all seen values in a sorted list, insert each new value at the correct position, return the element at index `size - k`. O(n) per call.
> **Key insight:** Min-heap of size K always holds exactly the K largest values seen so far. `peek()` is the Kth largest — never need to look at older values.
> **Approach:** Min-heap of size K. On `add`: offer the value, if size > K poll. Return `peek()`.

```java
public int add(int val) {
    pq.offer(val);
    // Evict smallest — heap always holds exactly the K largest seen so far
    if (pq.size() > k) pq.poll();
    // Top of size-K min-heap = the Kth largest element
    return pq.peek();
}
```

---

### LC 378: Kth Smallest Element in a Sorted Matrix

> **Problem:** Given an `n x n` matrix where each row and column is sorted in ascending order, find the kth smallest element. Example: `matrix = [[1,5,9],[10,11,13],[12,13,15]], k = 8` → `13`.

> **Brute force:** Collect all matrix elements into an array, sort it, return the K-th element. O(mn log mn) time, O(mn) space.
> **Key insight:** Treat each row as a sorted list — same as Merge K Sorted Lists. Each poll gives the next globally smallest element; the K-th poll is the answer.
> **Approach:** Min-heap seeded with first column (or first row). Poll smallest, push its right neighbor. After K polls, the answer is the last polled value. Similar to Merge K Sorted Lists (each row is a sorted list).

```java
// (a, b) -> a[0] - b[0]: min-heap by value (first element of the int[] triple)
PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> a[0] - b[0]);
// 🔄 Fallback: new PriorityQueue<>((a, b) -> Integer.compare(a[0], b[0]))
// Seed heap with the first element of each row: {value, row, col}
for (int i = 0; i < matrix.length; i++) pq.offer(new int[]{matrix[i][0], i, 0});
int val = 0;
// Poll k times — each poll gives the next smallest element in the matrix
for (int i = 0; i < k; i++) {
    int[] curr = pq.poll();
    val = curr[0];
    int row = curr[1], col = curr[2];
    // Push the next element from the same row (right neighbor)
    if (col + 1 < matrix[0].length) pq.offer(new int[]{matrix[row][col+1], row, col+1});
}
return val;
```

**Complexity (optimal):** O(K log n) time, O(n) space — n = number of rows (heap size)

---

### LC 767: Reorganize String

> **Problem:** Rearrange a string so that no two adjacent characters are the same. Return `""` if impossible. Example: `s = "aab"` → `"aba"`.

> **Brute force:** Try all permutations of the string, return the longest valid one. Exponential time.
> **Key insight:** Always placing the most frequent character (that isn't the previous one) greedily avoids impossible adjacency — max-heap gives the most frequent in O(log n) each step.
> **Approach:** Pattern 5 variant — max-heap by frequency. Greedily place the most frequent char, then place the next most frequent. Keep a "previous" char that can't be placed again until next round.

```java
// Build freq[26], add to max-heap
// Each step: poll top, append to result, hold previous
// Offer previous back (if count > 0), set current as new previous
```

**Complexity (optimal):** O(n log n) time, O(n) space — n = string length

---

### LC 1046: Last Stone Weight

> **Problem:** Smash the two heaviest stones together. If equal, both destroyed. If not, the lighter is destroyed and the heavier loses that weight. Return the weight of the last remaining stone (or 0). Example: `stones = [2,7,4,1,8,1]` → `1`.

> **Brute force:** Find the two largest stones by linear scan each round, smash them, repeat. O(n²) total — one scan per smash.
> **Key insight:** Max-heap gives the two heaviest stones in O(log n) per smash instead of O(n) scan.
> **Approach:** Max-heap. Poll two largest, push difference back if nonzero. Repeat until ≤1 stone left.

```java
// reverseOrder(): flips default min-heap → max-heap (heaviest stone at top)
PriorityQueue<Integer> pq = new PriorityQueue<>(Collections.reverseOrder());
// 🔄 Fallback: new PriorityQueue<>((a, b) -> b - a)
for (int s : stones) pq.offer(s);
while (pq.size() > 1) {
    // Smash the two heaviest stones together
    int a = pq.poll(), b = pq.poll();
    // If unequal, the leftover fragment goes back into the heap
    if (a != b) pq.offer(a - b);
}
// Either one stone remains or all were destroyed
return pq.isEmpty() ? 0 : pq.peek();
```

**Complexity (optimal):** O(n log n) time, O(n) space

---

### LC 215: Kth Largest Element in an Array

> **Problem:** Find the kth largest element (not kth distinct). Example: `nums = [3,2,1,5,6,4], k = 2` → `5`.

> **Brute force:** Sort the array, return element at index `n - k`. O(n log n) time.
> **Key insight:** Size-K min-heap holds exactly the K largest — `peek()` is the Kth largest. No need to rank all n elements.
> **Approach:** Min-heap of size K. Add all elements; if size > K, poll. After processing, `peek()` = kth largest. O(n log k).

```java
// Default min-heap — smallest element at top acts as the "gatekeeper"
PriorityQueue<Integer> pq = new PriorityQueue<>();
for (int num : nums) {
    pq.offer(num);
    // Evict the smallest — only the K largest survive
    if (pq.size() > k) pq.poll();
}
// Smallest of the top-K = the Kth largest overall
return pq.peek();
```

**Complexity (optimal):** O(n log k) time, O(k) space

---

### LC 692: Top K Frequent Words

> **Problem:** Return the k most frequent words, sorted by frequency (ties broken alphabetically). Example: `words = ["i","love","leetcode","i","love","coding"], k = 2` → `["i","love"]`.

> **Brute force:** Build frequency map, sort all unique words by frequency (then alphabetically), take first K. O(n log n) time.
> **Key insight:** Min-heap of size K — evict the word that should rank lowest (least frequent, or last alphabetically at equal frequency). The comparator handles both criteria.
> **Approach:** Frequency map + min-heap of size K. Comparator: sort by frequency ascending, then reverse alphabetical (so smallest freq/last-alphabetical gets evicted first).

```java
// Comparator: freq ascending (lowest freq evicted first from min-heap of size K)
//   Same freq? → reverse alphabetical (b.compareTo(a)) so last-alpha gets evicted
//   b.compareTo(a) returns positive if b > a alphabetically → b comes first → a evicted
PriorityQueue<String> pq = new PriorityQueue<>(
    (a, b) -> freq.get(a).equals(freq.get(b)) ? b.compareTo(a) : freq.get(a) - freq.get(b)
);
// 🔄 Fallback: sort list by freq descending + alpha ascending, take first K
// Collections.sort(words, (a, b) -> freq.get(b) != freq.get(a) ? freq.get(b) - freq.get(a) : a.compareTo(b));
for (String w : freq.keySet()) {
    pq.offer(w);
    // Evict the word with lowest freq (or last alphabetically at same freq)
    if (pq.size() > k) pq.poll();
}
```

**Complexity (optimal):** O(n log k) time, O(n + k) space

---

### LC 480: Sliding Window Median

> **Problem:** Find the median of each sliding window of size k. Example: `nums = [1,3,-1,-3,5,3,6,7], k = 3` → `[1.0,-1.0,-1.0,3.0,5.0,6.0]`.

> **Brute force:** Sort each window of size K, return the median. O(n × K log K) time, O(K) space.
> **Key insight:** Extend the two-heap structure from LC 295 with lazy deletion — mark leaving elements as deleted, skip them when they surface at the top. Avoids O(n) removal cost.
> **Approach:** Two-heap pattern (same as LC 295) + lazy deletion. Maintain max-heap (left) and min-heap (right). As window slides, add new element and logically remove the leaving element (mark as deleted, clean up when it appears at top).

```java
// Same two-heap structure as LC 295
// Key difference: track elements to remove with a HashMap<Integer, Integer> (count)
// On rebalance, skip elements that are in the "to-delete" map
```

**Complexity (optimal):** O(n log K) time, O(K) space

---

### LC 1405: Longest Happy String

> **Problem:** Build the longest string using at most `a` 'a's, `b` 'b's, `c` 'c's. No three consecutive same characters. Example: `a=1, b=1, c=7` → `"ccbccacc"`.

> **Brute force:** Try all valid character orderings, return the longest. Exponential time.
> **Key insight:** Always picking the most frequent available character (that doesn't violate the two-in-a-row rule) greedily maximizes length. Max-heap gives the most frequent in O(log n) each step.
> **Approach:** Greedy + max-heap. Always pick the most frequent char. If last two chars are the same, pick the second most frequent instead.

```java
// Max-heap by count. Each round:
// If top char was used twice in a row → pick second char
// Else → pick top char, append, decrement count, re-offer if count > 0
```

**Complexity (optimal):** O(n log n) time, O(n) space

---

## ⚠️ Interview Gotchas

### Edge cases interviewers probe
- **k = 1** — just find the max/min (heap still works, but they might expect you to recognize it's simpler)
- **k = n** — return all elements sorted (heap degenerates to a full sort)
- **Empty input** — handle gracefully before creating the heap
- **Stream problems** — heap is initialized once but `add()` is called many times — constructor vs per-call cost matters

### Min-heap vs Max-heap confusion (most common bug)
- **Top K largest → MIN-heap of size K** (evict the smallest of the top-K)
- **Top K smallest → MAX-heap of size K** (evict the largest of the bottom-K)
- **Running median → BOTH** (max-heap for left, min-heap for right)
- If you use the wrong heap type, you'll get the OPPOSITE of what you want

### Follow-up questions to expect
- "Can you do better than O(n log k)?" → Quickselect gives average O(n) for Kth element, but worst-case O(n²)
- "What if K is very close to N?" → Heap is still O(n log k), but sorting might be simpler
- "What if the stream is very large?" → Heap uses O(k) space — constant relative to stream size
- "What if elements can be removed?" → Need a lazy deletion heap or TreeMap

### Complexity traps
- `PriorityQueue.remove(Object)` is O(n) — NOT O(log n). Don't use it in a loop.
- Building a heap from an array is O(n), not O(n log n) — but Java's PriorityQueue doesn't expose this (you add one by one = O(n log n))
- Two-heap median: `addNum` is O(log n), `findMedian` is O(1) — interviewers want to hear both

---

## 🧩 Speed Drill — 8 Minutes

**Part 1 — Pattern Recognition (2 minutes)**
For each problem description, name the pattern in under 5 seconds:

1. "Find the 3rd largest element in an array" → ___
2. "Merge 5 sorted linked lists" → ___
3. "Find the running median of a stream" → ___
4. "Find the K most frequent words" → ___
5. "Smash two heaviest stones together repeatedly" → ___

**Part 2 — Write the Template (3 minutes)**
From memory, write the Kth-Largest template (Pattern 2) and the Merge-K-Sorted template (Pattern 3).

**Part 3 — Adapt (3 minutes)**
Solve LC 1046 (Last Stone Weight) using a max-heap. Time yourself.

**Scoring:**
- Part 1: 5/5 correct → ready. Got min/max heap confused → re-read the "bouncer" visual.
- Part 2: Both templates correct with right comparator → ready. Forgot `dummy node` in merge-K → re-read Pattern 3.
- Part 3: Under 2 minutes → ready. Over 3 minutes → drill more.

---

## 🔗 Cross-References

- **Companion Reference:** `../Reference/arraydeque-and-queue-reference.md` — PriorityQueue method signatures
- **Arrays & Hashing:** `../Interview/arrays-and-hashing.md` — Pattern 7 (Frequency + Bucket Sort) is an alternative to heaps for top-K
- **Linked List:** `../Interview/linked-list.md` — merge two sorted lists (Pattern 3 here generalizes it to K lists)
- **Binary Search:** `../Interview/binary-search.md` — LC 378 can also be solved with binary search on value range

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| May 2026 | **File created.** Heaps & Priority Queues Interview Playbook — 5 patterns (Top-K, Kth Element, Merge K Sorted, Two Heaps, Greedy+Heap), canonical walkthrough (LC 215), 10 problems with expanded definitions. |
| May 2026 | **Lambda/fallback pass.** Added 🔄 Lambda section with PQ comparator, `Collections.reverseOrder()`, `merge`, `Integer.compare` explanations + fallbacks. Inline comments + `🔄 Fallback` at all 12 PriorityQueue/merge/reverseOrder usage points across patterns and problem bank. |
| June 2026 | **Brute force + key insight pass.** Added `**What this solves**`, `**Brute force**`, `**Key insight**` to all 5 pattern blocks. Added `> **Brute force**`, `> **Key insight**`, and `**Complexity (optimal)**` to all 13 problem bank entries. Format matches AGENTS.md spec. |
