# InMobi SDE-II — Top 20 DSA Problems (Temp Prep File)

> **Interview:** Tomorrow morning (Fri). **Format:** 80 min, 2 problems (often 1 Medium + 1 Hard). InMobi leans HARDER than most — expect follow-ups like "now without recursion" or "now with obstacles."
>
> **⚠️ KEY INSIGHT — they evaluate PSEUDO CODE quality, NOT full running code.** The official PDF says: "pattern recognition, optimization, complexity analysis, pseudo-code with edge cases." So: crystal-clear variable names, edge case handling, stated complexity. Syntax mistakes are fine — unclear logic is not.
>
> **How to use:** For each problem: read brute force → read optimal → understand the key insight. ~3 min per problem = 1 hour total. Problems 16–20 are from your existing INTERVIEW_PREP.md (officially reported at InMobi or highly iDSP-relevant). **Apply the 5-Minute Wall from `QuickRef/interview-execution-process.md`.**
>
> **For LLD + Problem Solving rounds:** See `/Users/k0b077v/Documents/Kpl-inv/Inmobi/INTERVIEW_PREP.md` — has Parking Lot walkthrough, 5 design patterns, SOLID, 8-step PS framework, and two full PS walkthroughs (MySQL→MongoDB migration, daily spend cap).
>
> **Sources:** LeetCode discuss (SDE2 experiences 2022–2025), GeeksforGeeks InMobi sets, Glassdoor, GitHub company-wise lists, official InMobi interview guide PDFs.

---

## InMobi's Favorite Topics (from interviews + official guide)

```
Trees / BST:      #1 topic — BST operations, serialize, tree DP
DP:               #2 — classic DP + Hard DP (Russian Doll, Wildcard)
Heaps:            merge K sorted, connect ropes, top K, sliding window max
Binary Search:    rotated arrays, 2D matrix search
Linked Lists:     merge, remove nth, K sorted, LRU Cache
Arrays/Strings:   intervals, product, sliding window
Design/Stack:     FreqStack, LRU Cache (cache = heart of iDSP)
Graphs:           cheapest flights K stops (reported at InMobi!)
```

---

## 1. LC 56 — Merge Intervals ⭐⭐⭐ (directly asked)

**Trigger:** "overlapping intervals", "merge"

**Brute:** For each interval, compare with all others and merge. O(n²).

**Optimal:** Sort by start. Iterate — if current overlaps last merged, extend end. Else add new. **O(n log n).**

```java
Arrays.sort(intervals, (a, b) -> a[0] - b[0]);
List<int[]> merged = new ArrayList<>();
for (int[] interval : intervals) {
    if (merged.isEmpty() || merged.get(merged.size() - 1)[1] < interval[0]) {
        merged.add(interval);                     // no overlap → add
    } else {
        merged.get(merged.size() - 1)[1] = Math.max(
            merged.get(merged.size() - 1)[1], interval[1]);  // overlap → extend
    }
}
```

**Key insight:** After sorting by start, you only compare with the LAST merged interval. One pass.

---

## 2. LC 33 — Search in Rotated Sorted Array ⭐⭐⭐ (directly asked)

**Trigger:** "rotated sorted", "search"

**Brute:** Linear scan. O(n).

**Optimal:** Binary search — one half is always sorted. Check which half → decide where target could be. **O(log n).**

```java
int lo = 0, hi = nums.length - 1;
while (lo <= hi) {
    int mid = lo + (hi - lo) / 2;
    if (nums[mid] == target) { return mid; }
    if (nums[lo] <= nums[mid]) {                         // left half sorted
        if (target >= nums[lo] && target < nums[mid]) { hi = mid - 1; }
        else { lo = mid + 1; }
    } else {                                             // right half sorted
        if (target > nums[mid] && target <= nums[hi]) { lo = mid + 1; }
        else { hi = mid - 1; }
    }
}
return -1;
```

**Key insight:** `nums[lo] <= nums[mid]` — the `=` handles the 2-element case.

---

## 3. LC 62 — Unique Paths ⭐⭐⭐ (directly asked — expect follow-ups!)

**Trigger:** "grid paths", "right or down"

**Brute:** Recursion — try right and down at each cell. O(2^(m+n)).

**Optimal:** Grid DP — `dp[i][j] = dp[i-1][j] + dp[i][j-1]`. **O(m×n).**

```java
int[][] dp = new int[m][n];
for (int i = 0; i < m; i++) { dp[i][0] = 1; }
for (int j = 0; j < n; j++) { dp[0][j] = 1; }
for (int i = 1; i < m; i++) {
    for (int j = 1; j < n; j++) {
        dp[i][j] = dp[i - 1][j] + dp[i][j - 1];
    }
}
return dp[m - 1][n - 1];
```

**InMobi follow-ups you WILL get:**
- "Now with obstacles" (LC 63) → if obstacle, `dp[i][j] = 0`
- "Now without extra space" → use 1D array, update in-place
- "Now give me the memoized recursive version" → top-down with `memo[i][j]`

---

## 4. LC 238 — Product of Array Except Self ⭐⭐⭐ (online assessment)

**Trigger:** "product of all elements except current", "no division"

**Brute:** For each i, multiply all other elements. O(n²).

**Optimal:** Two passes — prefix product (left→right), then suffix product (right→left). **O(n), O(1) extra space.**

```java
int[] result = new int[n];
result[0] = 1;
for (int i = 1; i < n; i++) {
    result[i] = result[i - 1] * nums[i - 1];  // prefix product
}
int suffix = 1;
for (int i = n - 2; i >= 0; i--) {
    suffix *= nums[i + 1];
    result[i] *= suffix;                       // multiply by suffix product
}
return result;
```

**Key insight:** `result[i] = (product of everything LEFT of i) × (product of everything RIGHT of i)`. Build left-to-right first, then multiply right-to-left.

---

## 5. LC 3 — Longest Substring Without Repeating Characters ⭐⭐⭐

**Trigger:** "longest substring", "no repeating"

**Brute:** Check all substrings for uniqueness. O(n³).

**Optimal:** Sliding window — expand right, shrink left when duplicate. **O(n).**

```java
Map<Character, Integer> count = new HashMap<>();
int left = 0, maxLen = 0;
for (int right = 0; right < s.length(); right++) {
    char c = s.charAt(right);
    count.merge(c, 1, Integer::sum);
    while (count.get(c) > 1) {
        count.merge(s.charAt(left), -1, Integer::sum);
        left++;
    }
    maxLen = Math.max(maxLen, right - left + 1);
}
return maxLen;
```

**Key insight:** Window always contains unique chars. When duplicate enters, shrink from left until it's unique again.

---

## 6. LC 19 — Remove Nth Node From End of List ⭐⭐ (asked 2024)

**Trigger:** "remove nth from end", "linked list"

**Brute:** Two passes — count length, then remove at `len - n`. O(n).

**Optimal:** One pass — gap pointer (fast is n steps ahead of slow). When fast hits end, slow is at the node BEFORE the target. **O(n), one pass.**

```java
ListNode dummy = new ListNode(0, head);
ListNode fast = dummy, slow = dummy;
for (int i = 0; i <= n; i++) {
    fast = fast.next;            // fast is n+1 ahead of slow
}
while (fast != null) {
    fast = fast.next;
    slow = slow.next;
}
slow.next = slow.next.next;      // skip the target node
return dummy.next;
```

**Key insight:** Dummy node handles edge case of removing the head. Fast moves n+1 ahead (not n) so slow lands ONE BEFORE the target.

---

## 7. LC 653 — Two Sum IV - Input is a BST ⭐⭐ (directly asked)

**Trigger:** "two elements in BST with sum = k"

**Brute:** Inorder → sorted array → two pointers. O(n) time, O(n) space.

**Optimal (same complexity, cleaner):** HashSet + DFS. For each node, check if `k - node.val` is in set. **O(n) time, O(n) space.**

```java
Set<Integer> seen = new HashSet<>();

boolean find(TreeNode node, int k) {
    if (node == null) { return false; }
    if (seen.contains(k - node.val)) { return true; }
    seen.add(node.val);
    return find(node.left, k) || find(node.right, k);
}
```

**Key insight:** It's just Two Sum on a tree. Check-then-add discipline applies — `seen.contains()` before `seen.add()`.

**Follow-up:** "Can you do O(1) space?" → BST iterator (controlled inorder) with two pointers converging. O(h) space.

---

## 8. LC 337 — House Robber III ⭐⭐⭐ (directly asked — "max sum, no two adjacent in tree")

**Trigger:** "binary tree", "no two adjacent nodes", "maximize sum"

**Brute:** For each node, try rob/skip, recurse on all children/grandchildren. O(2^n) exponential.

**Optimal:** Bottom-up DFS. Each node returns TWO values: `[robMe, skipMe]`. **O(n).**

```java
int[] dfs(TreeNode node) {
    if (node == null) { return new int[]{0, 0}; }
    int[] left = dfs(node.left);
    int[] right = dfs(node.right);

    // Rob this node → must skip children
    int rob = node.val + left[1] + right[1];
    // Skip this node → take best of rob/skip for each child
    int skip = Math.max(left[0], left[1]) + Math.max(right[0], right[1]);

    return new int[]{rob, skip};
}
// Answer: Math.max(result[0], result[1])
```

**Key insight:** Each node returns a pair `[rob, skip]`. Parent uses children's `skip` value when robbing, and `max(rob, skip)` when skipping. No grandchild access needed — the pair carries everything.

---

## 9. LC 285 — Inorder Successor in BST ⭐⭐ (directly asked)

**Trigger:** "inorder successor", "next node in BST"

**Brute:** Full inorder traversal → find node → return next. O(n).

**Optimal:** Use BST property. Go left when `root.val > p.val` (potential successor). Go right otherwise. **O(h).**

```java
TreeNode inorderSuccessor(TreeNode root, TreeNode p) {
    TreeNode successor = null;
    while (root != null) {
        if (root.val > p.val) {
            successor = root;     // this COULD be the successor
            root = root.left;     // try to find a closer (smaller) one
        } else {
            root = root.right;    // too small or equal → go right
        }
    }
    return successor;
}
```

**Key insight:** Every time you go LEFT, the current node is a potential successor (it's bigger than p). The LAST such node before hitting null is the answer.

---

## 10. LC 297 — Serialize and Deserialize Binary Tree ⭐⭐⭐ (directly asked)

**Trigger:** "serialize", "deserialize", "convert tree to string"

**Brute (still optimal):** Preorder DFS with null markers. **O(n).**

```java
// SERIALIZE: preorder, mark nulls as "#"
String serialize(TreeNode root) {
    if (root == null) { return "#"; }
    return root.val + "," + serialize(root.left) + "," + serialize(root.right);
}

// DESERIALIZE: split by comma, rebuild using a queue
TreeNode deserialize(String data) {
    Queue<String> queue = new ArrayDeque<>(Arrays.asList(data.split(",")));
    return build(queue);
}

TreeNode build(Queue<String> queue) {
    String val = queue.poll();
    if (val.equals("#")) { return null; }
    TreeNode node = new TreeNode(Integer.parseInt(val));
    node.left = build(queue);
    node.right = build(queue);
    return node;
}
```

**Key insight:** Preorder + null markers = unambiguous encoding. The queue in deserialize naturally processes tokens in the same preorder sequence.

---

## 11. LC 23 — Merge K Sorted Lists ⭐⭐⭐ (directly asked)

**Trigger:** "merge k sorted"

**Brute:** Merge lists one by one. O(n×k).

**Optimal:** Min-heap of size k. Always poll smallest head → advance that list. **O(n log k).**

```java
PriorityQueue<ListNode> heap = new PriorityQueue<>((a, b) -> a.val - b.val);
for (ListNode head : lists) {
    if (head != null) { heap.offer(head); }
}
ListNode dummy = new ListNode(0), curr = dummy;
while (!heap.isEmpty()) {
    ListNode node = heap.poll();
    curr.next = node;
    curr = curr.next;
    if (node.next != null) { heap.offer(node.next); }
}
return dummy.next;
```

**Key insight:** Heap always has at most k nodes (one from each list). Log k per operation × n total nodes = O(n log k).

---

## 12. LC 322 — Coin Change ⭐⭐⭐ (DP fundamental — variations asked)

**Trigger:** "minimum coins", "make amount"

**Brute:** Try all combinations recursively. O(amount^coins).

**Optimal:** DP — `dp[i]` = min coins for amount i. **O(amount × coins).**

```java
int[] dp = new int[amount + 1];
Arrays.fill(dp, Integer.MAX_VALUE);
dp[0] = 0;
for (int i = 1; i <= amount; i++) {
    for (int coin : coins) {
        if (coin <= i && dp[i - coin] != Integer.MAX_VALUE) {
            dp[i] = Math.min(dp[i], dp[i - coin] + 1);
        }
    }
}
return dp[amount] == Integer.MAX_VALUE ? -1 : dp[amount];
```

**Key insight:** Init with MAX_VALUE (impossible). Check `dp[i-coin] != MAX_VALUE` to avoid overflow.

---

## 13. LC 354 — Russian Doll Envelopes ⭐⭐⭐ (asked MULTIPLE TIMES at InMobi 2025!)

**Trigger:** "envelopes", "nesting", "maximum number that fit"

**Brute:** Try all orderings and check nesting. O(2^n).

**Optimal:** Sort by width ASC, then by height DESC (for same width). Then LIS on heights. **O(n log n).**

```java
// Sort: width ASC. If same width → height DESC (prevents using two same-width envelopes)
Arrays.sort(envelopes, (a, b) -> a[0] == b[0] ? b[1] - a[1] : a[0] - b[0]);

// LIS on heights using tails + binary search
List<Integer> tails = new ArrayList<>();
for (int[] env : envelopes) {
    int h = env[1];
    int pos = Collections.binarySearch(tails, h);
    if (pos < 0) { pos = -(pos + 1); }
    if (pos == tails.size()) {
        tails.add(h);
    } else {
        tails.set(pos, h);
    }
}
return tails.size();
```

**Key insight:** After sorting by width, the problem reduces to 1D LIS on heights. Height DESC for same width prevents two envelopes with same width from both being selected. This is the O(n log n) LIS you just learned!

---

## 14. LC 44 — Wildcard Matching ⭐⭐⭐ (directly asked)

**Trigger:** "wildcard", "pattern matching", `?` and `*`

**Brute:** Recursion trying all `*` expansions. O(2^(m+n)).

**Optimal:** 2D DP — `dp[i][j]` = does `s[0..i]` match `p[0..j]`? **O(m×n).**

```java
boolean[][] dp = new boolean[m + 1][n + 1];
dp[0][0] = true;
// Base: p = "***" matches empty string
for (int j = 1; j <= n; j++) {
    if (p.charAt(j - 1) == '*') { dp[0][j] = dp[0][j - 1]; }
}
for (int i = 1; i <= m; i++) {
    for (int j = 1; j <= n; j++) {
        if (p.charAt(j - 1) == s.charAt(i - 1) || p.charAt(j - 1) == '?') {
            dp[i][j] = dp[i - 1][j - 1];         // chars match → diagonal
        } else if (p.charAt(j - 1) == '*') {
            dp[i][j] = dp[i][j - 1]               // * matches empty
                     || dp[i - 1][j];              // * matches one more char
        }
    }
}
return dp[m][n];
```

**Key insight:** `*` has two choices: match empty (`dp[i][j-1]`) or match one more char from s (`dp[i-1][j]`). The `dp[i-1][j]` naturally handles matching multiple chars because it chains.

---

## 15. LC 895 — Maximum Frequency Stack ⭐⭐⭐ (asked at InMobi/Glance)

**Trigger:** "frequency stack", "pop most frequent"

**Brute:** Track freq, scan for max-freq element on every pop. O(n) per pop.

**Optimal:** Map of stacks — one stack per frequency level. `maxFreq` tracks current highest. **O(1) push, O(1) pop.**

```java
Map<Integer, Integer> freq = new HashMap<>();           // val → current freq
Map<Integer, Deque<Integer>> group = new HashMap<>();   // freq → stack of vals
int maxFreq = 0;

void push(int val) {
    int f = freq.merge(val, 1, Integer::sum);
    group.computeIfAbsent(f, k -> new ArrayDeque<>()).push(val);
    maxFreq = Math.max(maxFreq, f);
}

int pop() {
    int val = group.get(maxFreq).pop();
    if (group.get(maxFreq).isEmpty()) { maxFreq--; }
    freq.merge(val, -1, Integer::sum);
    return val;
}
```

**Key insight:** Group values by frequency. Push adds to the current frequency's stack. Pop always takes from `maxFreq`'s stack. If that stack empties, `maxFreq--`. Both O(1).

---

---

## — FROM OFFICIAL INMOBI PREP / REPORTED ASKED —

---

## 16. LC 146 — LRU Cache ⭐⭐⭐ (iDSP context: caching is EVERYTHING)

**Trigger:** "design LRU", "evict least recently used"

**Brute:** LinkedHashMap with removeEldestEntry. O(1) but feels like cheating.

**Optimal:** HashMap + Doubly Linked List. Map for O(1) lookup, DLL for O(1) move-to-front and evict-tail. **O(1) get, O(1) put.**

```java
class LRUCache {
    Map<Integer, Node> map = new HashMap<>();
    Node head = new Node(0, 0), tail = new Node(0, 0);
    int cap;

    LRUCache(int capacity) {
        cap = capacity;
        head.next = tail;
        tail.prev = head;
    }

    int get(int key) {
        if (!map.containsKey(key)) { return -1; }
        Node n = map.get(key);
        remove(n);
        insertHead(n);
        return n.val;
    }

    void put(int key, int value) {
        if (map.containsKey(key)) { remove(map.get(key)); }
        if (map.size() == cap) {
            remove(tail.prev);            // evict LRU
        }
        insertHead(new Node(key, value));
    }

    void remove(Node n) {
        map.remove(n.key);
        n.prev.next = n.next;
        n.next.prev = n.prev;
    }

    void insertHead(Node n) {
        map.put(n.key, n);
        n.next = head.next;
        n.prev = head;
        head.next.prev = n;
        head.next = n;
    }
}
```

**Key insight:** Sentinel head/tail nodes eliminate null checks. Every access moves node to head. Eviction is always `tail.prev`. The HashMap gives O(1) key→node lookup; the DLL gives O(1) reordering.

**iDSP relevance:** *"In iDSP, every bidder keeps an in-memory LRU for frequency caps, feature caches, and pacing state. This is literally the data structure behind the hot path."*

---

## 17. LC 239 — Sliding Window Maximum ⭐⭐⭐ (streaming pattern — iDSP loves this)

**Trigger:** "maximum in sliding window", "max in window of size k"

**Brute:** For each window, scan for max. O(n × k).

**Optimal:** Monotonic decreasing deque. Front is always the max. **O(n).**

```java
Deque<Integer> deque = new ArrayDeque<>();       // stores INDICES
int[] result = new int[n - k + 1];
for (int i = 0; i < n; i++) {
    // Remove elements outside window
    while (!deque.isEmpty() && deque.peekFirst() < i - k + 1) {
        deque.pollFirst();
    }
    // Remove smaller elements from back (they'll never be max)
    while (!deque.isEmpty() && nums[deque.peekLast()] < nums[i]) {
        deque.pollLast();
    }
    deque.offerLast(i);
    if (i >= k - 1) {
        result[i - k + 1] = nums[deque.peekFirst()];
    }
}
```

**Key insight:** Deque stores **indices**, not values. It's always decreasing (front = biggest). Two cleanups per iteration: expired (out of window) from front, smaller-than-current from back. Each element enters and leaves deque exactly once → O(n).

---

## 18. LC 787 — Cheapest Flights Within K Stops ⭐⭐⭐ (reported at InMobi!)

**Trigger:** "cheapest path", "at most K stops", "flights"

**Brute:** DFS trying all paths. Exponential.

**Optimal:** Modified Bellman-Ford — relax all edges K+1 times. **O(K × E).**

```java
int[] cost = new int[n];
Arrays.fill(cost, Integer.MAX_VALUE);
cost[src] = 0;
for (int i = 0; i <= k; i++) {
    // Copy to avoid using updates from THIS round
    int[] temp = Arrays.copyOf(cost, n);
    for (int[] flight : flights) {
        int u = flight[0], v = flight[1], w = flight[2];
        if (cost[u] != Integer.MAX_VALUE) {
            temp[v] = Math.min(temp[v], cost[u] + w);
        }
    }
    cost = temp;
}
return cost[dst] == Integer.MAX_VALUE ? -1 : cost[dst];
```

**Key insight:** Must copy `cost` before each round — otherwise you might use costs from edges relaxed in the SAME round (which means more stops than allowed). K+1 rounds = at most K intermediate stops.

**Alternative:** Dijkstra with state `(cost, node, stops)` — don't skip visited if fewer stops remain.

---

## 19. LC 240 — Search a 2D Matrix II ⭐⭐ (reported at InMobi!)

**Trigger:** "search in 2D matrix", "rows and columns sorted"

**Brute:** Binary search each row. O(m × log n).

**Optimal:** Start at top-right corner. If target > current, go down. If target < current, go left. **O(m + n).**

```java
int row = 0, col = matrix[0].length - 1;
while (row < matrix.length && col >= 0) {
    if (matrix[row][col] == target) { return true; }
    if (matrix[row][col] > target) {
        col--;       // too big → eliminate this column
    } else {
        row++;       // too small → eliminate this row
    }
}
return false;
```

**Key insight:** Top-right (or bottom-left) is the only starting point where you can eliminate a full row OR column with each comparison. That's what makes it O(m+n).

---

## 20. Connect N Ropes / Min Cost to Merge ⭐⭐ (GFG — reported at InMobi)

**Trigger:** "connect ropes minimum cost", "merge cost", "min cost to combine"

**Brute:** Always merge the two smallest. Without a heap, finding two smallest = O(n) per merge → O(n²).

**Optimal:** Min-heap. Pop two smallest, push their sum, accumulate cost. **O(n log n).**

```java
PriorityQueue<Integer> heap = new PriorityQueue<>();
for (int rope : ropes) {
    heap.offer(rope);
}
int totalCost = 0;
while (heap.size() > 1) {
    int first = heap.poll();
    int second = heap.poll();
    int merged = first + second;
    totalCost += merged;
    heap.offer(merged);
}
return totalCost;
```

**Key insight:** Greedy — always merge the two cheapest. The heap maintains sorted order after each merge in O(log n). This is the same pattern as Huffman coding.

---

## ⚡ Quick Reference — Pattern Recognition

```
Intervals / "overlapping"        → Sort by start, merge (Problem 1)
"Rotated sorted"                 → Binary search, check which half (Problem 2)
"Grid paths"                     → Grid DP (Problem 3)
"Product except self"            → Prefix × suffix (Problem 4)
"Longest substring"              → Sliding window (Problem 5)
"Remove nth from end"            → Gap pointer with dummy (Problem 6)
"Two sum in BST"                 → HashSet + DFS (Problem 7)
"Max sum, no adjacent in tree"   → Tree DP: return [rob, skip] (Problem 8)
"Inorder successor BST"          → Go left = candidate, go right = too small (Problem 9)
"Serialize/deserialize tree"     → Preorder + null markers (Problem 10)
"Merge K sorted"                 → Min-heap of K heads (Problem 11)
"Min coins / make amount"        → DP: dp[i] = min(dp[i-coin]+1) (Problem 12)
"Envelopes / Russian doll"       → Sort + LIS on second dim (Problem 13)
"Wildcard matching"              → 2D DP: * = empty OR one more (Problem 14)
"Pop most frequent"              → Map-of-stacks by frequency (Problem 15)
"Design LRU cache"               → HashMap + DLL, sentinel nodes (Problem 16) ★
"Max in sliding window"          → Monotonic decreasing deque of indices (Problem 17) ★
"Cheapest flights K stops"       → Bellman-Ford K+1 rounds, COPY before relax (Problem 18) ★
"Search 2D sorted matrix"        → Start top-right, eliminate row or col (Problem 19) ★
"Connect ropes / min merge cost" → Min-heap greedy, always merge two smallest (Problem 20) ★
```

*(★ = from official InMobi prep / reported asked)*

---

## ⚠️ InMobi-Specific Tips

1. **They evaluate PSEUDO CODE quality** — not full running code. Clear variable names + edge cases matter more than perfect syntax. This is NOT Salesforce.
2. **80-minute rounds** — you have more time. USE the 5-Minute Wall. Don't rush.
3. **Expect follow-ups** — "Now do it without recursion" / "Now with obstacles" / "Now in O(1) space"
4. **They love Trees + BST** — problems 7, 8, 9, 10 are ALL trees. Know your BST properties cold.
5. **Russian Doll was asked MULTIPLE times in 2025** — this is their current favorite. Nail problem 13.
6. **LRU Cache is extremely likely** — caching is the heart of iDSP. Problem 16.
7. **They ask Java questions too** — be ready for 5-6 quick Java questions (collections, generics, threads)
8. **Cheapest Flights + Search 2D Matrix** — both explicitly reported asked at InMobi. Problems 18 & 19.

## 🗺️ Other Rounds — Quick Pointers

**Full prep for LLD + PS:** `/Users/k0b077v/Documents/Kpl-inv/Inmobi/INTERVIEW_PREP.md`

**LLD (11:30am):** Parking Lot is the canonical example in their rubric. Know 5 patterns: Strategy, Factory, Observer, Singleton, Chain of Responsibility. Lead with interfaces. Mention thread-safety.

**PS (after break):** NOT system design. It's a "business problem solved with DS + logic." Use the 8-step framework: restate → 4+ clarifying Qs → assumptions → brute force → bottleneck → optimize → trade-off table → operational concerns.

**iDSP context to drop:** *"At millions QPS with <100ms latency, the hot path can't touch a DB synchronously — so everything pushes to in-memory state on the bidder, async sync to central, eventual consistency."*

---

**Sources:**
- [InMobi SDE2 Bangalore 2022 — LeetCode](https://leetcode.com/discuss/interview-experience/2198805/InMobi-or-SDE2-or-Bangalore.or-June-2022)
- [InMobi SDE-2 Backend Rejected — LeetCode](https://leetcode.com/discuss/interview-experience/5543516/InMobi-or-SDE-2-(Backend)-Bangalore-or-Rejected/)
- [InMobi SDE2 Remote 2022 — LeetCode](https://leetcode.com/discuss/interview-experience/1798125/inmobi-sde2-bangaloreremote-2022)
- [InMobi Multi-Company SDE2 — LeetCode](https://leetcode.com/discuss/interview-experience/1148169/inmobi-microsoft-flipkart-intuit-soroco-sde2/)
- [InMobi On-Campus — GeeksforGeeks](https://www.geeksforgeeks.org/interview-experiences/inmobi-interview-experience-on-campus/)
- [InMobi Set 2 On-Campus — GeeksforGeeks](https://www.geeksforgeeks.org/inmobi-interview-eexperience-set-2on-campus/)
- [InMobi Set 4 — GeeksforGeeks](https://www.geeksforgeeks.org/inmobi-interview-experience-set-4/)
- [InMobi 2024 On-Campus — GeeksforGeeks](https://www.geeksforgeeks.org/interview-experiences/inmobi-interview-experience-2024-on-campus/)
- [Glassdoor InMobi Interview Questions](https://www.glassdoor.co.in/Interview/InMobi-Interview-Questions-E373348.htm)
