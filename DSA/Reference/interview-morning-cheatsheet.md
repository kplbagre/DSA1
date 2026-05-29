# Interview Morning Cheatsheet

> **Read this once over coffee. 15 minutes max.** Every pattern you need for arrays, two pointers, sliding window, hashmap, and linked list in one page. No fluff, no explanations — just the template skeleton and the one line that makes it work.

---

## ⚡ Universal Imports Block — Write First

```java
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
```

```java
// For linked list problems — write this if not provided:
class ListNode {
    int val;
    ListNode next;
    ListNode() {}
    ListNode(int val) { this.val = val; }
    ListNode(int val, ListNode next) { this.val = val; this.next = next; }
}
```

---

## 1. Arrays — Key Patterns

### Kadane's (Max Subarray Sum)
```java
long best = nums[0], curr = nums[0];
for (int i = 1; i < nums.length; i++) {
    curr = Math.max(nums[i], curr + nums[i]);
    best = Math.max(best, curr);
}
```

### Prefix Sum + HashMap (Subarray Sum = K)
```java
Map<Long, Integer> map = new HashMap<>();
map.put(0L, 1);
long prefix = 0;
int count = 0;
for (int x : nums) {
    prefix += x;
    count += map.getOrDefault(prefix - k, 0);
    map.merge(prefix, 1, Integer::sum);
}
```

### Dutch National Flag (Sort 0/1/2)
```java
int lo = 0, mid = 0, hi = nums.length - 1;
while (mid <= hi) {
    if (nums[mid] == 0) { swap(lo, mid); lo++; mid++; }
    else if (nums[mid] == 1) { mid++; }
    else { swap(mid, hi); hi--; }
}
```

### Moore's Voting (Majority Element)
```java
int cand = 0, cnt = 0;
for (int x : nums) {
    if (cnt == 0) { cand = x; }
    cnt += (x == cand) ? 1 : -1;
}
```

### Cyclic Sort (Values 1..n, find missing/duplicate)
```java
int i = 0;
while (i < n) {
    int correct = nums[i] - 1;
    if (nums[i] != nums[correct]) { swap(i, correct); }
    else { i++; }
}
```

---

## 2. Two Pointers

### Converging (Sorted array, pair sum)
```java
int l = 0, r = nums.length - 1;
while (l < r) {
    int sum = nums[l] + nums[r];
    if (sum == target) { return new int[]{l, r}; }
    if (sum < target) { l++; } else { r--; }
}
```

### Same-Direction (In-place dedup)
```java
int slow = 0;
for (int fast = 1; fast < nums.length; fast++) {
    if (nums[fast] != nums[slow]) { slow++; nums[slow] = nums[fast]; }
}
return slow + 1;
```

---

## 3. Sliding Window — 4 Templates

### T1: Fixed Window (size K)
```java
long sum = 0;
for (int i = 0; i < k; i++) { sum += nums[i]; }
long best = sum;
for (int r = k; r < n; r++) {
    sum += nums[r] - nums[r - k];
    best = Math.max(best, sum);
}
```

### T2: Longest Valid ⭐
```java
int l = 0, ans = 0;
for (int r = 0; r < n; r++) {
    // add nums[r] to state
    while (invalid()) {
        // remove nums[l] from state
        l++;
    }
    ans = Math.max(ans, r - l + 1);
}
```

### T3: Shortest Valid
```java
int l = 0, ans = Integer.MAX_VALUE;
long sum = 0;
for (int r = 0; r < n; r++) {
    sum += nums[r];
    while (sum >= target) {
        ans = Math.min(ans, r - l + 1);
        sum -= nums[l++];
    }
}
return ans == Integer.MAX_VALUE ? 0 : ans;
```

### T4: Exactly K = atMost(K) - atMost(K-1) ⭐
```java
private int atMost(int[] nums, int k) {
    int l = 0, count = 0, distinct = 0;
    Map<Integer, Integer> freq = new HashMap<>();
    for (int r = 0; r < nums.length; r++) {
        if (freq.getOrDefault(nums[r], 0) == 0) { distinct++; }
        freq.merge(nums[r], 1, Integer::sum);
        while (distinct > k) {
            freq.merge(nums[l], -1, Integer::sum);
            if (freq.get(nums[l]) == 0) { distinct--; }
            l++;
        }
        count += r - l + 1;
    }
    return count;
}
```

---

## 4. HashMap — Key Patterns

### Frequency Map
```java
Map<Integer, Integer> freq = new HashMap<>();
for (int x : nums) { freq.merge(x, 1, Integer::sum); }
```
> For lowercase chars only: `int[] freq = new int[26]; freq[c - 'a']++;`

### Two Sum (Value → Index)
```java
Map<Integer, Integer> map = new HashMap<>();
for (int i = 0; i < nums.length; i++) {
    int comp = target - nums[i];
    if (map.containsKey(comp)) { return new int[]{map.get(comp), i}; }
    map.put(nums[i], i);
}
```

### Group Anagrams (Canonical Key)
```java
Map<String, List<String>> groups = new HashMap<>();
for (String s : strs) {
    char[] c = s.toCharArray();
    Arrays.sort(c);
    groups.computeIfAbsent(new String(c), k -> new ArrayList<>()).add(s);
}
```

### Top K Frequent (Map + Min-Heap)
```java
Map<Integer, Integer> freq = new HashMap<>();
for (int n : nums) { freq.merge(n, 1, Integer::sum); }
PriorityQueue<int[]> heap = new PriorityQueue<>((a, b) -> a[1] - b[1]);
for (var e : freq.entrySet()) {
    heap.offer(new int[]{e.getKey(), e.getValue()});
    if (heap.size() > k) { heap.poll(); }
}
```

### Longest Consecutive Sequence (Set + Start-of-Run)
```java
Set<Integer> set = new HashSet<>();
for (int v : nums) { set.add(v); }
int best = 0;
for (int v : set) {
    if (!set.contains(v - 1)) {
        int len = 1, curr = v;
        while (set.contains(curr + 1)) { curr++; len++; }
        best = Math.max(best, len);
    }
}
```

---

## 5. Linked List — Key Patterns

### Reverse (3-pointer) ⭐
```java
ListNode prev = null, curr = head;
while (curr != null) {
    ListNode next = curr.next;
    curr.next = prev;
    prev = curr;
    curr = next;
}
return prev;
```

### Fast/Slow — Find Middle
```java
ListNode slow = head, fast = head;
while (fast != null && fast.next != null) {
    slow = slow.next;
    fast = fast.next.next;
}
return slow;
```

### Detect Cycle
```java
ListNode slow = head, fast = head;
while (fast != null && fast.next != null) {
    slow = slow.next;
    fast = fast.next.next;
    if (slow == fast) { return true; }
}
return false;
```

### Find Cycle Start (Floyd's)
```java
// After slow == fast (detected cycle):
ListNode entry = head;
while (entry != slow) { entry = entry.next; slow = slow.next; }
return entry;
```

### Merge Two Sorted Lists
```java
ListNode dummy = new ListNode(0), tail = dummy;
while (l1 != null && l2 != null) {
    if (l1.val <= l2.val) { tail.next = l1; l1 = l1.next; }
    else { tail.next = l2; l2 = l2.next; }
    tail = tail.next;
}
tail.next = (l1 != null) ? l1 : l2;
return dummy.next;
```

### Remove N-th from End
```java
ListNode dummy = new ListNode(0, head), fast = dummy, slow = dummy;
for (int i = 0; i <= n; i++) { fast = fast.next; }
while (fast != null) { slow = slow.next; fast = fast.next; }
slow.next = slow.next.next;
return dummy.next;
```

### Add Two Numbers (with carry)
```java
ListNode dummy = new ListNode(0), curr = dummy;
int carry = 0;
while (l1 != null || l2 != null || carry != 0) {
    int sum = carry;
    if (l1 != null) { sum += l1.val; l1 = l1.next; }
    if (l2 != null) { sum += l2.val; l2 = l2.next; }
    carry = sum / 10;
    curr.next = new ListNode(sum % 10);
    curr = curr.next;
}
return dummy.next;
```

### Palindrome Check (Middle + Reverse + Compare)
```java
// 1. Find middle (slow)
// 2. Reverse from slow onward → rev
// 3. Compare head vs rev node by node
```

### Intersection of Two Lists
```java
ListNode a = headA, b = headB;
while (a != b) {
    a = (a != null) ? a.next : headB;
    b = (b != null) ? b.next : headA;
}
return a;
```

---

## 6. Top Gotchas — Quick Scan

| Bug | Fix |
| --- | --- |
| `int` sum overflow | Use `long` |
| `Integer` `==` past 127 | Use `.intValue()` or `.equals()` |
| `Arrays.sort(int[])` not stable | Box to `Integer[]` for stable sort |
| `(a, b) -> a - b` overflow | `Integer.compare(a, b)` |
| `Integer[]` memo not `int[]` | Null = uncomputed, 0 = valid answer |
| Forgot `carry != 0` in loop | `while (l1 != null \|\| l2 != null \|\| carry != 0)` |
| Reverse: save `next` BEFORE overwriting | `ListNode next = curr.next;` first |
| Fast/slow: null check on `fast.next` | `fast != null && fast.next != null` |
| `Map.get()` auto-unbox NPE | `getOrDefault(key, 0)` |
| `freq.merge(k, -1, ...)` leaves 0 | `if (freq.get(k) == 0) freq.remove(k)` |
| Prefix sum: forgot seed `map.put(0, 1)` | Misses subarrays starting at index 0 |
| Sliding window with negatives | NOT sliding window — use prefix sum |
| Off-by-one on `right - left + 1` | Inclusive both ends |

---

## 7. Pattern Picker — 10-Second Decision

```
"Pair/triplet on sorted array"       → Two Pointers Converging
"In-place dedup / move zeros"        → Two Pointers Same-Direction
"Longest subarray where..."          → Sliding Window T2
"Shortest subarray where..."         → Sliding Window T3
"Exactly K of something"             → atMost(K) - atMost(K-1)
"Subarray sum = K"                   → Prefix Sum + HashMap
"Max subarray sum"                   → Kadane's
"Have I seen this before?"           → HashSet
"Value → index lookup"               → HashMap
"Frequency counting"                 → HashMap (or int[26])
"Group by some property"             → Canonical Key + HashMap
"Top K frequent"                     → Map + Min-Heap
"Values are 1..n"                    → Cyclic Sort
"Majority element"                   → Moore's Voting
"Reverse linked list"                → prev/curr/next 3-pointer
"Find middle of list"                → Fast/Slow
"Cycle in list?"                     → Fast/Slow (Floyd's)
"Merge two sorted lists"             → Dummy + compare
"Head might change"                  → Dummy head
```

---

> **Cross-references:** Full details in `arrays-reference.md`, `two-pointers-sliding-window-reference.md`, `hashmap-section-updated.md`, `linkedlist-reference.md`. All in `DSA/Reference/`.

> **Last updated:** May 2026.
