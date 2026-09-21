# EPAM Systems — Top 45 DSA Interview Problems

> **Source:** EPAM Systems most-frequently-asked problems, grouped by difficulty.
> **Format per problem:** Clean problem statement → interview framing → brute force → path to optimal → solution code → complexity derivation.
> **Language:** Java throughout. SQL problems get SQL solutions.

---

## 📋 Table of Contents

### 🟢 Easy (13 problems)

| # | Problem | Pattern | LC # |
|---|---------|---------|------|
| 1 | [Two Sum](#1-two-sum--lc-1) | Array, Hash Table | 1 |
| 2 | [Merge Sorted Array](#2-merge-sorted-array--lc-88) | Array, Two Pointers | 88 |
| 3 | [Move Zeroes](#3-move-zeroes--lc-283) | Array, Two Pointers | 283 |
| 4 | [Palindrome Number](#4-palindrome-number--lc-9) | Math | 9 |
| 5 | [Valid Parentheses](#5-valid-parentheses--lc-20) | String, Stack | 20 |
| 6 | [Best Time to Buy and Sell Stock](#6-best-time-to-buy-and-sell-stock--lc-121) | Array, DP | 121 |
| 7 | [Valid Anagram](#7-valid-anagram--lc-242) | Hash Table, String | 242 |
| 8 | [Longest Common Prefix](#8-longest-common-prefix--lc-14) | String | 14 |
| 9 | [Reverse String](#9-reverse-string--lc-344) | Two Pointers, String | 344 |
| 10 | [First Unique Character in a String](#10-first-unique-character-in-a-string--lc-387) | Hash Table, String | 387 |
| 11 | [Duplicate Emails](#11-duplicate-emails--lc-196) | Database (SQL) | 196 |
| 12 | [Reverse Vowels of a String](#12-reverse-vowels-of-a-string--lc-345) | Two Pointers, String | 345 |
| 13 | [Remove Duplicates from Sorted Array](#13-remove-duplicates-from-sorted-array--lc-26) | Array, Two Pointers | 26 |

### 🟡 Medium (30 problems)

| # | Problem | Pattern | LC # |
|---|---------|---------|------|
| 14 | [Query Kth Smallest Trimmed Number](#14-query-kth-smallest-trimmed-number--lc-2343) | Array, String, Sort | 2343 |
| 15 | [Max Consecutive Ones III](#15-max-consecutive-ones-iii--lc-1004) | Sliding Window | 1004 |
| 16 | [Remove K Digits](#16-remove-k-digits--lc-402) | Stack, Greedy | 402 |
| 17 | [Decode Ways](#17-decode-ways--lc-91) | String, DP | 91 |
| 18 | [Longest Substring Without Repeating Characters](#18-longest-substring-without-repeating-characters--lc-3) | Sliding Window | 3 |
| 19 | [Longest Palindromic Substring](#19-longest-palindromic-substring--lc-5) | Two Pointers, DP | 5 |
| 20 | [Group Anagrams](#20-group-anagrams--lc-49) | Hash Table, String | 49 |
| 21 | [LRU Cache](#21-lru-cache--lc-146) | HashMap, Linked List, Design | 146 |
| 22 | [Add Two Numbers](#22-add-two-numbers--lc-2) | Linked List, Math | 2 |
| 23 | [3Sum](#23-3sum--lc-15) | Array, Two Pointers, Sorting | 15 |
| 24 | [Minimum Operations to Make a Uni-Value Grid](#24-minimum-operations-to-make-a-uni-value-grid--lc-2033) | Array, Math, Sorting | 2033 |
| 25 | [Asteroid Collision](#25-asteroid-collision--lc-735) | Array, Stack | 735 |
| 26 | [Coin Change](#26-coin-change--lc-322) | Array, DP | 322 |
| 27 | [House Robber](#27-house-robber--lc-198) | Array, DP | 198 |
| 28 | [Partition Equal Subset Sum](#28-partition-equal-subset-sum--lc-416) | Array, DP | 416 |
| 29 | [Maximum Difference Between Node and Ancestor](#29-maximum-difference-between-node-and-ancestor--lc-1026) | Tree, DFS | 1026 |
| 30 | [Reverse Integer](#30-reverse-integer--lc-7) | Math | 7 |
| 31 | [Longest Consecutive Sequence](#31-longest-consecutive-sequence--lc-128) | Array, HashSet | 128 |
| 32 | [String Compression](#32-string-compression--lc-443) | Two Pointers, String | 443 |
| 33 | [Rotate Array](#33-rotate-array--lc-189) | Array, Math, Two Pointers | 189 |
| 34 | [Two Sum II - Input Array Is Sorted](#34-two-sum-ii---input-array-is-sorted--lc-167) | Array, Two Pointers | 167 |
| 35 | [Pow(x, n)](#35-powx-n--lc-50) | Math, Recursion | 50 |
| 36 | [Merge Intervals](#36-merge-intervals--lc-56) | Array, Sorting | 56 |
| 37 | [Generate Parentheses](#37-generate-parentheses--lc-22) | Backtracking | 22 |
| 38 | [Count Salary Categories](#38-count-salary-categories--lc-1907) | Database (SQL) | 1907 |
| 39 | [Multiply Strings](#39-multiply-strings--lc-43) | Math, String, Simulation | 43 |
| 40 | [Maximum Subarray](#40-maximum-subarray--lc-53) | Array, DP (Kadane's) | 53 |
| 41 | [Reverse Words in a String](#41-reverse-words-in-a-string--lc-151) | Two Pointers, String | 151 |
| 42 | [Unique Paths](#42-unique-paths--lc-62) | Math, DP | 62 |
| 43 | [Edit Distance](#43-edit-distance--lc-72) | String, DP | 72 |

---

## 🟢 Easy Problems

---

## 1. Two Sum — LC 1

**Difficulty:** Easy | **Pattern:** Hash Table | **Tags:** Array, Hash Table

### 📌 Problem Statement

Given an array of integers `nums` and an integer `target`, return the indices of the two numbers that add up to `target`. Exactly one valid answer exists. You may not use the same element twice.

```
Input:  nums = [2, 7, 11, 15], target = 9
Output: [0, 1]   // nums[0] + nums[1] = 2 + 7 = 9
```

### 🎤 Interview Framing

EPAM uses this as a warmup to check if you think about trade-offs. The expected conversation: "Can you avoid nested loops?" → "What are you trading when you use a HashMap?" → "What if there could be duplicate values?" Treat the follow-up about duplicates seriously — the solution handles it naturally (we check before inserting), but you should be able to explain it.

### ❌ Brute Force

Try every pair `(i, j)` where `i < j`. Check if `nums[i] + nums[j] == target`.

**Complexity:** Time O(n²) — two nested loops over n elements. Space O(1).

### 🧠 From Brute to Optimal

The nested loop is slow because for each element we scan the entire remaining array looking for its complement. **Key insight:** instead of scanning forward, ask "have I already seen `target - nums[i]`?" Store everything we've visited in a HashMap of `value → index`. One pass is enough.

Why check before inserting? If we insert first, a single element could "match" with itself (e.g., target=6, nums[i]=3 — we'd find 3 in the map even though it's the same element). Check first, insert after.

### ✅ Optimal Solution

**Steps:**

1. Create a HashMap to store `value → index` for elements seen so far.
2. For each element, compute `complement = target - nums[i]`.
3. If complement exists in the map, return both indices.
4. Otherwise, store current element in the map and move on.

```java
public int[] twoSum(int[] nums, int target) {
    // Step 1: value → index lookup
    Map<Integer, Integer> seen = new HashMap<>();
    for (int i = 0; i < nums.length; i++) {
        // Step 2: what do we need?
        int complement = target - nums[i];
        // Step 3: already seen it?
        if (seen.containsKey(complement)) {
            return new int[]{ seen.get(complement), i };
        }
        // Step 4: remember this element
        seen.put(nums[i], i);
    }
    return new int[]{};
}
```

### 📊 Complexity

- **Time: O(n)** — single pass; each HashMap lookup and insert is O(1) amortized.
- **Space: O(n)** — in the worst case (no pair found until last two elements), we store all n elements in the map.

---

## 2. Merge Sorted Array — LC 88

**Difficulty:** Easy | **Pattern:** Two Pointers (merge from back) | **Tags:** Array, Two Pointers, Sorting

### 📌 Problem Statement

Two sorted integer arrays: `nums1` has `m` real elements followed by `n` zeros (padding). `nums2` has `n` elements. Merge `nums2` into `nums1` in sorted order, in-place.

```
Input:  nums1 = [1,2,3,0,0,0], m = 3
        nums2 = [2,5,6],       n = 3
Output: [1,2,2,3,5,6]
```

### 🎤 Interview Framing

The classic follow-up is: *"Why not merge from the front?"* Merging from the front requires shifting elements right to make space — O(n²) in the worst case. This is a setup to see if you recognize that the extra zeros at the end of `nums1` are the key resource. Mention it before they ask.

### ❌ Brute Force

Copy `nums2` into the empty slots of `nums1`, then call `Arrays.sort(nums1)`.
**Complexity:** Time O((m+n) log(m+n)). Space O(1). Wastes the fact that both inputs are already sorted.

### 🧠 From Brute to Optimal

Both arrays are sorted. We can merge in O(m+n) like the merge step in merge sort — but merging from the front would overwrite `nums1` elements we haven't processed yet. The empty zeros at the end of `nums1` are free space. Fill from the back: compare the largest unprocessed elements of each array, place the bigger one at the current write position (starting at m+n-1). Three pointers: `p1 = m-1`, `p2 = n-1`, `write = m+n-1`.

### ✅ Optimal Solution

**Steps:**

1. Set three pointers: `p1` at end of nums1's real data, `p2` at end of nums2, `write` at end of full nums1.
2. Compare elements at p1 and p2 — place the larger one at the write position.
3. Repeat until all of nums2 is placed. Remaining nums1 elements are already in place.

```java
public void merge(int[] nums1, int m, int[] nums2, int n) {
    // Step 1: start from the back
    int p1 = m - 1;
    int p2 = n - 1;
    int write = m + n - 1;
    // Step 2–3: merge from back, largest first
    while (p2 >= 0) {
        if (p1 >= 0 && nums1[p1] > nums2[p2]) {
            nums1[write--] = nums1[p1--];
        } else {
            nums1[write--] = nums2[p2--];
        }
    }
    // nums1 leftovers already in place — done
}
```

### 📊 Complexity

- **Time: O(m+n)** — each element is placed exactly once; both pointers move monotonically.
- **Space: O(1)** — write into the existing `nums1` array, no extra allocation.

---

## 3. Move Zeroes — LC 283

**Difficulty:** Easy | **Pattern:** Two Pointers (slow-fast) | **Tags:** Array, Two Pointers

### 📌 Problem Statement

Given an integer array `nums`, move all zeroes to the end while maintaining the relative order of non-zero elements. Do it in-place with minimum operations.

```
Input:  [0, 1, 0, 3, 12]
Output: [1, 3, 12, 0, 0]
```

### 🎤 Interview Framing

EPAM often follows up: *"Can you minimize the number of write operations?"* The naive approach writes zeros at the end — that's extra writes. The swap approach only writes when it finds a non-zero. Talk about this trade-off.

### ❌ Brute Force

Collect all non-zero elements into a list. Write them back to the start of `nums`. Fill the rest with zeros.
**Complexity:** Time O(n). Space O(n) — the temporary list. Two passes.

### 🧠 From Brute to Optimal

We need O(1) space. Use a slow pointer that tracks where the next non-zero element should land. Fast pointer walks the array — when it finds a non-zero, swap it with the slow pointer position and advance slow. All positions before slow are non-zero and in original order. All positions from slow onward are already zero (because we moved the non-zeros out) — no need to fill zeros at the end.

### ✅ Optimal Solution

**Steps:**

1. Initialize `slow` at 0 — the write position for the next non-zero.
2. Walk `fast` across the entire array.
3. When `fast` finds a non-zero, swap it with `slow` position and advance `slow`.

```java
public void moveZeroes(int[] nums) {
    // Step 1: slow = next position for a non-zero
    int slow = 0;
    // Step 2: fast scans everything
    for (int fast = 0; fast < nums.length; fast++) {
        // Step 3: found non-zero → swap into place
        if (nums[fast] != 0) {
            int temp = nums[slow];
            nums[slow] = nums[fast];
            nums[fast] = temp;
            slow++;
        }
    }
}
```

### 📊 Complexity

- **Time: O(n)** — single pass with fast pointer over all n elements.
- **Space: O(1)** — two index variables; swap is in-place.

---

## 4. Palindrome Number — LC 9

**Difficulty:** Easy | **Pattern:** Math | **Tags:** Math

### 📌 Problem Statement

Given an integer `x`, return `true` if `x` is a palindrome (reads the same forwards and backwards). Do not convert to a string.

```
121   → true
-121  → false  (reads 121- backwards — negative not palindrome)
10    → false  (reads 01 backwards — leading zero)
```

### 🎤 Interview Framing

"Without converting to string" is the constraint that makes this interesting. EPAM wants to see mathematical digit manipulation. Edge cases to state upfront: all negatives are false; numbers ending in 0 (except 0 itself) are false.

### ❌ Brute Force

Convert `x` to a string, use two pointers to check palindrome. O(d) where d = number of digits. Simple but uses O(d) space for the string.

### 🧠 From Brute to Optimal

We only need to check if the number equals its reverse. But we don't need to reverse all digits — if we reverse just the second half and compare with the first half, we avoid the integer overflow risk of reversing large numbers. Stop when `reversed >= x` — at that point we've processed the second half.

- Even digits: `x == reversed`
- Odd digits: `x == reversed / 10` (middle digit doesn't matter)

### ✅ Optimal Solution

**Steps:**

1. Handle edge cases: negatives are false; trailing-zero numbers (except 0) are false.
2. Reverse the second half by extracting digits with `% 10`.
3. Stop when `reversed >= x` — we've processed the second half.
4. Compare: even-length → `x == reversed`; odd-length → `x == reversed / 10`.

```java
public boolean isPalindrome(int x) {
    // Step 1: edge cases
    if (x < 0 || (x % 10 == 0 && x != 0)) {
        return false;
    }
    // Step 2–3: reverse only the second half
    int reversed = 0;
    while (x > reversed) {
        reversed = reversed * 10 + x % 10;
        x /= 10;
    }
    // Step 4: compare halves
    return x == reversed || x == reversed / 10;
}
```

### 📊 Complexity

- **Time: O(log₁₀ n)** — we process half the digits; number of digits = log₁₀(x).
- **Space: O(1)** — only two integer variables.

---

## 5. Valid Parentheses — LC 20

**Difficulty:** Easy | **Pattern:** Stack | **Tags:** String, Stack

### 📌 Problem Statement

Given a string containing only `(`, `)`, `{`, `}`, `[`, `]`, return `true` if every opening bracket is closed by the correct type and in the correct order.

```
"()"      → true
"()[]{}"  → true
"(]"      → false
"([)]"    → false
```

### 🎤 Interview Framing

Clean stack problem. EPAM interviewers sometimes ask: *"What's in the stack at any point?"* Answer: only opening brackets. The invariant is that `stack.peek()` is always the most recent unmatched opening bracket — and it must be the one that the next closing bracket should close.

### ❌ Brute Force

Repeatedly scan the string, remove adjacent matched pairs like `()`, `[]`, `{}` until the string is empty or no pairs remain. If empty, valid.
**Complexity:** Time O(n²) — each scan is O(n), and we may need O(n) scans. Space O(n) for the string.

### 🧠 From Brute to Optimal

The brute force removes pairs from the middle. But brackets must be closed in LIFO order — the most recently opened must be closed first. That's exactly what a stack models. Push every opening bracket. When you see a closing bracket, the top of the stack must be its matching opener — if not, invalid. If the stack is empty at the end, valid.

### ✅ Optimal Solution

**Steps:**

1. Initialize a stack for unmatched opening brackets.
2. For each character: if opening bracket → push.
3. If closing bracket → pop and verify it matches the expected opener.
4. At end, stack must be empty for valid input.

```java
public boolean isValid(String s) {
    // Step 1: stack for unmatched openers
    Deque<Character> stack = new ArrayDeque<>();
    for (char c : s.toCharArray()) {
        // Step 2: opening → push
        if (c == '(' || c == '[' || c == '{') {
            stack.push(c);
        } else {
            // Step 3: closing → must match top
            if (stack.isEmpty()) {
                return false;
            }
            char top = stack.pop();
            if (c == ')' && top != '(') {
                return false;
            }
            if (c == ']' && top != '[') {
                return false;
            }
            if (c == '}' && top != '{') {
                return false;
            }
        }
    }
    // Step 4: all brackets matched?
    return stack.isEmpty();
}
```

### 📊 Complexity

- **Time: O(n)** — single pass; each character is pushed and popped at most once.
- **Space: O(n)** — worst case all opening brackets (e.g., `"(((("`), all go on the stack.

---

## 6. Best Time to Buy and Sell Stock — LC 121

**Difficulty:** Easy | **Pattern:** Greedy / Sliding Window | **Tags:** Array, Dynamic Programming

### 📌 Problem Statement

Given an array `prices` where `prices[i]` is the stock price on day `i`, find the maximum profit from exactly one buy-sell transaction. You must buy before you sell. Return 0 if no profit is possible.

```
Input:  [7, 1, 5, 3, 6, 4]
Output: 5   // Buy at 1, sell at 6
```

### 🎤 Interview Framing

Follow-up in interviews: *"What if you could do unlimited transactions?"* (LC 122 — buy every valley, sell every peak). *"What if you could do at most 2 transactions?"* (LC 123 — harder DP). Know that LC 121 is the single-transaction base case. State the constraint clearly: sell price must come after buy price.

### ❌ Brute Force

Try every pair `(buy day i, sell day j)` where `j > i`. Compute `prices[j] - prices[i]`, track max.
**Complexity:** Time O(n²). Space O(1).

### 🧠 From Brute to Optimal

We want to maximize `prices[j] - prices[i]` with `j > i`. For a fixed sell day `j`, the best buy is the minimum price in `prices[0..j-1]`. So: walk through prices left to right, maintain the minimum price seen so far. At each day, compute profit if we sold today = `prices[j] - minSoFar`. Track the global max profit.

### ✅ Optimal Solution

**Steps:**

1. Track `minPrice` seen so far (init to MAX_VALUE).
2. For each price: if lower than minPrice → update minPrice.
3. Otherwise compute profit = price − minPrice → update maxProfit if larger.

```java
public int maxProfit(int[] prices) {
    // Step 1: cheapest buy price so far
    int minPrice = Integer.MAX_VALUE;
    int maxProfit = 0;
    for (int price : prices) {
        // Step 2: new minimum?
        if (price < minPrice) {
            minPrice = price;
        // Step 3: better profit if we sell today?
        } else if (price - minPrice > maxProfit) {
            maxProfit = price - minPrice;
        }
    }
    return maxProfit;
}
```

### 📊 Complexity

- **Time: O(n)** — single pass over n prices.
- **Space: O(1)** — two scalar variables regardless of input size.

---

## 7. Valid Anagram — LC 242

**Difficulty:** Easy | **Pattern:** Hash Table | **Tags:** Hash Table, String, Sorting

### 📌 Problem Statement

Given two strings `s` and `t`, return `true` if `t` is an anagram of `s` — i.e., they contain the same characters with the same frequencies.

```
s = "anagram", t = "nagaram"  → true
s = "rat",     t = "car"      → false
```

### 🎤 Interview Framing

Classic follow-up: *"What if the inputs contain Unicode characters?"* Then a fixed 26-element array won't work — use a `HashMap<Character, Integer>` instead. State this upfront to show awareness beyond ASCII.

### ❌ Brute Force

Sort both strings. If sorted versions are equal, they're anagrams.
**Complexity:** Time O(n log n) for sorting. Space O(n) for the sorted copies (or O(1) if sorting in-place).

### 🧠 From Brute to Optimal

We don't need order — just frequency. Count character frequencies in `s`, then subtract counts using `t`. If any count goes negative (or the lengths differ), not an anagram. Single pass over each string → O(n).

### ✅ Optimal Solution

**Steps:**

1. If lengths differ, return false immediately.
2. Use a 26-element frequency array.
3. Single pass: increment for each char in `s`, decrement for each char in `t`.
4. If all counts are zero → anagram.

```java
public boolean isAnagram(String s, String t) {
    // Step 1: length mismatch = not anagram
    if (s.length() != t.length()) {
        return false;
    }
    // Step 2: frequency tracker
    int[] count = new int[26];
    // Step 3: increment for s, decrement for t
    for (int i = 0; i < s.length(); i++) {
        count[s.charAt(i) - 'a']++;
        count[t.charAt(i) - 'a']--;
    }
    // Step 4: any non-zero count = mismatch
    for (int c : count) {
        if (c != 0) {
            return false;
        }
    }
    return true;
}
```

### 📊 Complexity

- **Time: O(n)** — two passes of length n (one combined, one over 26 fixed slots → O(1) effectively).
- **Space: O(1)** — fixed 26-element array regardless of input length.

---

## 8. Longest Common Prefix — LC 14

**Difficulty:** Easy | **Pattern:** String | **Tags:** String, Trie

### 📌 Problem Statement

Given an array of strings, find the longest common prefix among all strings. Return `""` if none exists.

```
Input:  ["flower","flow","flight"]
Output: "fl"

Input:  ["dog","racecar","car"]
Output: ""
```

### 🎤 Interview Framing

EPAM often frames this as: *"You have a list of configuration keys — find the shared namespace prefix."* Follow-up: *"What if the list is huge and you need to do this repeatedly?"* → Trie. For now, the vertical scan is expected.

### ❌ Brute Force

Compare every string against every other string character-by-character, intersecting their common prefixes.
**Complexity:** Time O(n × m) where m is the length of the shortest string. This is actually optimal in terms of character comparisons — but the code can be simplified significantly.

### 🧠 From Brute to Optimal

Take the first string as the candidate prefix. For each subsequent string, shorten the candidate while it's not a prefix of that string. If the candidate ever becomes empty, return immediately.

### ✅ Optimal Solution

**Steps:**

1. Take the first string as the candidate prefix.
2. For each subsequent string, shrink prefix while it's not a prefix of that string.
3. If prefix becomes empty at any point, return `""`.

```java
public String longestCommonPrefix(String[] strs) {
    if (strs == null || strs.length == 0) {
        return "";
    }
    // Step 1: start with full first string
    String prefix = strs[0];
    // Step 2: shrink prefix against each string
    for (int i = 1; i < strs.length; i++) {
        while (!strs[i].startsWith(prefix)) {
            prefix = prefix.substring(0, prefix.length() - 1);
            // Step 3: nothing in common
            if (prefix.isEmpty()) {
                return "";
            }
        }
    }
    return prefix;
}
```

### 📊 Complexity

- **Time: O(n × m)** — n strings, each compared up to m characters (length of shortest string).
- **Space: O(m)** — the prefix string, which shrinks as we compare; at most length of the first string.

---

## 9. Reverse String — LC 344

**Difficulty:** Easy | **Pattern:** Two Pointers | **Tags:** Two Pointers, String

### 📌 Problem Statement

Reverse an array of characters `s` in-place using O(1) extra memory.

```
Input:  ['h','e','l','l','o']
Output: ['o','l','l','e','h']
```

### 🎤 Interview Framing

Trivially easy — EPAM uses this to check that you state assumptions, handle even/odd length (the two-pointer approach handles both identically), and write clean code fast. Don't overthink; write it in 60 seconds and move to follow-ups: *"Reverse only the vowels"* (LC 345) or *"Reverse words in a string"* (LC 151).

### ❌ Brute Force

Copy all characters into a new array in reverse order. O(n) space.

### 🧠 From Brute to Optimal

Two pointers: left starts at 0, right at end. Swap and move inward. When they meet or cross, done. No extra space needed.

### ✅ Optimal Solution

**Steps:**

1. Set `left` at start, `right` at end.
2. Swap characters at both pointers.
3. Move pointers inward until they meet.

```java
public void reverseString(char[] s) {
    // Step 1: two pointers from both ends
    int left = 0;
    int right = s.length - 1;
    while (left < right) {
        // Step 2: swap
        char temp = s[left];
        s[left] = s[right];
        s[right] = temp;
        // Step 3: move inward
        left++;
        right--;
    }
}
```

### 📊 Complexity

- **Time: O(n)** — each element is touched once; we do n/2 swaps.
- **Space: O(1)** — single temp variable, in-place.

---

## 10. First Unique Character in a String — LC 387

**Difficulty:** Easy | **Pattern:** Hash Table | **Tags:** Hash Table, String, Queue

### 📌 Problem Statement

Given a string `s`, return the index of the first non-repeating character. Return -1 if none exists.

```
s = "leetcode"   → 0  (character 'l')
s = "loveleetcode" → 2  (character 'v')
s = "aabb"       → -1
```

### 🎤 Interview Framing

Follow-up: *"What if the string is streamed — you can't know when it ends?"* → Use a Queue to track insertion order; evict from the front when a character is seen twice. For the standard problem, two-pass with a frequency array is cleanest.

### ❌ Brute Force

For each character, scan the rest of the string to see if it appears again. Return the first that doesn't.
**Complexity:** Time O(n²). Space O(1).

### 🧠 From Brute to Optimal

First pass: count character frequencies (O(n)). Second pass: return the index of the first character with count == 1. Two passes, O(n) total.

### ✅ Optimal Solution

**Steps:**

1. First pass: count frequency of each character in a 26-element array.
2. Second pass: return the first index where frequency == 1.

```java
public int firstUniqChar(String s) {
    // Step 1: count frequencies
    int[] count = new int[26];
    for (char c : s.toCharArray()) {
        count[c - 'a']++;
    }
    // Step 2: find first unique
    for (int i = 0; i < s.length(); i++) {
        if (count[s.charAt(i) - 'a'] == 1) {
            return i;
        }
    }
    return -1;
}
```

### 📊 Complexity

- **Time: O(n)** — two passes over the string.
- **Space: O(1)** — fixed 26-element array (bounded alphabet).

---

## 11. Duplicate Emails — LC 196

**Difficulty:** Easy | **Pattern:** Database (SQL) | **Tags:** Database

### 📌 Problem Statement

Table `Person(id, email)`. Find all emails that appear more than once.

```
Person:
+----+---------+
| id | email   |
+----+---------+
| 1  | a@b.com |
| 2  | c@d.com |
| 3  | a@b.com |
+----+---------+

Output: ["a@b.com"]
```

### 🎤 Interview Framing

SQL problems at EPAM test whether you know grouping and filtering aggregates. The key distinction: `WHERE` filters rows before grouping; `HAVING` filters after. You can't use `WHERE COUNT(*) > 1` — you need `HAVING`.

### ❌ Brute Force (SQL)

Self-join `Person p1` with `Person p2` on `p1.email = p2.email AND p1.id <> p2.id`. Select distinct `p1.email`.
Works but generates a large intermediate result.

### ✅ Optimal Solution

**Steps:**

1. GROUP BY email to collapse duplicate rows.
2. HAVING COUNT(*) > 1 to keep only emails that appear more than once.

```sql
-- Step 1: group rows by email
SELECT email
FROM Person
GROUP BY email
-- Step 2: filter to duplicates only
HAVING COUNT(*) > 1;
```

### 📊 Complexity

- **Time: O(n log n)** — grouping requires sorting or hashing; most DB engines use a hash aggregate → O(n) average.
- **Space: O(n)** — the GROUP BY result set.

---

## 12. Reverse Vowels of a String — LC 345

**Difficulty:** Easy | **Pattern:** Two Pointers | **Tags:** Two Pointers, String

### 📌 Problem Statement

Given a string `s`, reverse only the vowels (`a, e, i, o, u` — both upper and lower case). All other characters stay in place.

```
Input:  "hello"
Output: "holle"

Input:  "leetcode"
Output: "leotcede"
```

### 🎤 Interview Framing

EPAM uses this as a variation of Reverse String to test "can you adapt the two-pointer pattern with a condition?" The trick: don't move a pointer past a consonant blindly — only advance when pointing at a vowel on both sides.

### ❌ Brute Force

Extract all vowels into a list. Walk the string; when you encounter a vowel, replace it with vowels from the list in reverse order.
**Complexity:** Time O(n). Space O(k) where k = number of vowels.

### 🧠 From Brute to Optimal

Two pointers: left starts at 0, right at end. Advance left until it points to a vowel. Retreat right until it points to a vowel. Swap. Repeat. O(1) extra space (using a char array).

### ✅ Optimal Solution

**Steps:**

1. Build a set of vowels (both upper and lower case).
2. Convert string to char array (Java strings are immutable).
3. Two pointers: advance `left` to next vowel, retreat `right` to next vowel.
4. Swap and repeat until pointers cross.

```java
public String reverseVowels(String s) {
    // Step 1: vowel lookup set
    Set<Character> vowels = new HashSet<>();
    for (char c : "aeiouAEIOU".toCharArray()) {
        vowels.add(c);
    }
    // Step 2: mutable copy
    char[] arr = s.toCharArray();
    int left = 0;
    int right = arr.length - 1;
    while (left < right) {
        // Step 3: find next vowel from each side
        while (left < right && !vowels.contains(arr[left])) {
            left++;
        }
        while (left < right && !vowels.contains(arr[right])) {
            right--;
        }
        // Step 4: swap vowels
        if (left < right) {
            char temp = arr[left];
            arr[left] = arr[right];
            arr[right] = temp;
            left++;
            right--;
        }
    }
    return new String(arr);
}
```

### 📊 Complexity

- **Time: O(n)** — each pointer traverses at most the full string once.
- **Space: O(n)** — char array copy of the string (required in Java since strings are immutable).

---

## 13. Remove Duplicates from Sorted Array — LC 26

**Difficulty:** Easy | **Pattern:** Two Pointers (slow-fast) | **Tags:** Array, Two Pointers

### 📌 Problem Statement

Given a sorted integer array `nums`, remove duplicates in-place so each unique value appears exactly once. Return the count `k` of unique elements. The first `k` elements of `nums` must hold the result in order.

```
Input:  [1, 1, 2]
Output: 2, nums = [1, 2, _]

Input:  [0,0,1,1,1,2,2,3,3,4]
Output: 5, nums = [0,1,2,3,4,_,_,_,_,_]
```

### 🎤 Interview Framing

The classic follow-up: *"What if each element can appear at most twice?"* (LC 80). Your solution extends naturally — just change the condition to `nums[fast] != nums[slow - 2]`. Mention this proactively.

### ❌ Brute Force

Use a HashSet to track seen values. Build a new array with unique values in order.
**Complexity:** Time O(n). Space O(n). Wastes the fact the array is sorted.

### 🧠 From Brute to Optimal

Because the array is sorted, duplicates are adjacent. Use a slow pointer that marks the "write position" — the next position a unique value should go. Fast pointer walks ahead. When `nums[fast] != nums[slow]`, we've found a new unique value — copy it to `slow+1` and advance slow.

### ✅ Optimal Solution

**Steps:**

1. Handle empty array edge case.
2. `slow` marks the last unique position (starts at 0 — first element is always unique).
3. `fast` scans ahead; when it finds a different value, copy it to `slow + 1` and advance `slow`.
4. Return `slow + 1` (count of unique elements).

```java
public int removeDuplicates(int[] nums) {
    // Step 1: edge case
    if (nums.length == 0) {
        return 0;
    }
    // Step 2: slow = last unique position
    int slow = 0;
    // Step 3: fast scans for new values
    for (int fast = 1; fast < nums.length; fast++) {
        if (nums[fast] != nums[slow]) {
            slow++;
            nums[slow] = nums[fast];
        }
    }
    // Step 4: count of uniques
    return slow + 1;
}
```

### 📊 Complexity

- **Time: O(n)** — single pass with fast pointer over all n elements.
- **Space: O(1)** — two index pointers; all writes go back into the input array.

---

## 🟡 Medium Problems

---

## 14. Query Kth Smallest Trimmed Number — LC 2343

**Difficulty:** Medium | **Pattern:** Sorting with custom key | **Tags:** Array, String, Divide and Conquer

### 📌 Problem Statement

You are given an array of strings `nums` (all equal-length, representing integers with leading zeros) and an array `queries`. Each query is `[k, trim]`: trim each number to its last `trim` digits, then return the index of the `k`th smallest trimmed number (1-indexed). Ties broken by original index.

```
nums    = ["102","473","251","814"]
queries = [[1,1],[2,3],[4,2],[1,2]]
Output  = [2, 2, 0, 0]
// Query [1,1]: trim to 1 digit → ["2","3","1","4"] → smallest is "1" at index 2
```

### 🎤 Interview Framing

This tests whether you can define a custom comparator on a transformed key. The natural follow-up: *"What if queries were sorted by trim length — could you amortize?"* → Radix sort approach. For the interview, per-query sort is expected.

### ❌ Brute Force

For each query, trim all numbers, sort by (trimmed value, original index), return the k-th.
**Complexity:** Time O(q × n log n × w) where q = queries, n = nums, w = word length. Fine for the given constraints.

### 🧠 From Brute to Optimal

Same as brute force but with a clean implementation — pair each trimmed value with its original index, sort, pick index k-1.

### ✅ Optimal Solution

**Steps:**

1. For each query, extract `k` and `trim`.
2. Trim each number to its last `trim` digits, pair with original index.
3. Sort pairs by (trimmed value, then original index for ties).
4. Pick the k-th smallest (index k−1).

```java
public int[] smallestTrimmedNumbers(String[] nums, int[][] queries) {
    int[] result = new int[queries.length];
    for (int q = 0; q < queries.length; q++) {
        // Step 1: extract query params
        int k = queries[q][0];
        int trim = queries[q][1];
        int n = nums.length;
        int len = nums[0].length();
        int start = len - trim;
        // Step 2: pair (original index, trimmed value)
        int[][] pairs = new int[n][2];
        for (int i = 0; i < n; i++) {
            pairs[i][0] = i;
            pairs[i][1] = Integer.parseInt(nums[i].substring(start));
        }
        // Step 3: sort by trimmed value, break ties by index
        Arrays.sort(pairs, (a, b) -> a[1] != b[1] ? a[1] - b[1] : a[0] - b[0]);
        // Step 4: k-th smallest
        result[q] = pairs[k - 1][0];
    }
    return result;
}
```

### 📊 Complexity

- **Time: O(q × n log n)** — q queries, each sorts n pairs.
- **Space: O(n)** — pairs array rebuilt per query.

---

## 15. Max Consecutive Ones III — LC 1004

**Difficulty:** Medium | **Pattern:** Sliding Window | **Tags:** Array, Binary Search, Sliding Window

### 📌 Problem Statement

Given a binary array `nums` and an integer `k`, return the maximum number of consecutive 1s if you can flip at most `k` zeros to ones.

```
Input:  nums = [1,1,1,0,0,0,1,1,1,1,0], k = 2
Output: 6
// Flip the two 0s at indices 9 and 10 → window [1,1,1,1,1,1] of length 6? 
// Actually flip indices 3,4 or 4,5 — best window is indices 5..10, length 6
```

### 🎤 Interview Framing

Reframe this as: *"find the longest subarray containing at most k zeros."* That reframing makes the sliding window template immediately applicable. Mention this reframing in the interview — it shows pattern recognition.

### ❌ Brute Force

Try every subarray. For each, count zeros. If zeros ≤ k, update max length.
**Complexity:** Time O(n²). Space O(1).

### 🧠 From Brute to Optimal

Classic variable-size sliding window: maintain a window `[left, right]` where zero count ≤ k. Expand right each step. If zero count exceeds k, shrink from left until the invariant is restored. Track max window size.

### ✅ Optimal Solution

**Steps:**

1. Maintain window `[left, right]` with a count of zeros inside.
2. Expand `right` — if new element is 0, increment zeroCount.
3. If zeroCount exceeds k, shrink from `left` until invariant restored.
4. Track max window size at each step.

```java
public int longestOnes(int[] nums, int k) {
    int left = 0;
    int zeroCount = 0;
    int maxLen = 0;
    // Step 2: expand right
    for (int right = 0; right < nums.length; right++) {
        if (nums[right] == 0) {
            zeroCount++;
        }
        // Step 3: shrink left until zeros ≤ k
        while (zeroCount > k) {
            if (nums[left] == 0) {
                zeroCount--;
            }
            left++;
        }
        // Step 4: update best window
        maxLen = Math.max(maxLen, right - left + 1);
    }
    return maxLen;
}
```

### 📊 Complexity

- **Time: O(n)** — each element is added to the window once and removed at most once; right and left both traverse the array once.
- **Space: O(1)** — three pointers/counters.

---

## 16. Remove K Digits — LC 402

**Difficulty:** Medium | **Pattern:** Monotonic Stack, Greedy | **Tags:** String, Stack, Greedy

### 📌 Problem Statement

Given a non-negative integer string `num` and an integer `k`, remove `k` digits so the remaining number is as small as possible. Return the result as a string without leading zeros.

```
Input:  num = "1432219", k = 3
Output: "1219"

Input:  num = "10200", k = 1
Output: "200"  → actually "200" → strip leading zero → "200"... wait: output is "200"
```

### 🎤 Interview Framing

Greedy insight: to minimize the number, we want the leftmost digit to be as small as possible. If a digit is larger than the next digit, removing it makes the number smaller. Use a monotone increasing stack — pop whenever the stack top is greater than the current digit and we still have k removals left.

### ❌ Brute Force

Try all C(n, k) combinations of removing k digits. Return the lexicographically smallest.
**Complexity:** Time O(C(n,k) × n) — exponential. Not feasible.

### 🧠 From Brute to Optimal

Greedy: process digits left to right. Maintain a stack that's monotonically non-decreasing. For each new digit, while the stack top is larger than the current digit AND k > 0, pop (that's one removal). Push the current digit. After processing all digits, if k > 0 still, remove from the tail (which is already sorted ascending — remove the largest). Strip leading zeros.

### ✅ Optimal Solution

**Steps:**

1. Process digits left to right, maintaining a monotone non-decreasing stack.
2. For each digit: while stack top > current digit AND k > 0, pop (use one removal).
3. Push current digit.
4. If k still > 0 after all digits, remove from the tail.
5. Build result string, strip leading zeros.

```java
public String removeKdigits(String num, int k) {
    // Step 1: monotone stack
    Deque<Character> stack = new ArrayDeque<>();
    for (char digit : num.toCharArray()) {
        // Step 2: pop larger digits (greedy removal)
        while (!stack.isEmpty() && k > 0 && stack.peek() > digit) {
            stack.pop();
            k--;
        }
        // Step 3: push current digit
        stack.push(digit);
    }
    // Step 4: remove remaining from tail if k left
    while (k > 0) {
        stack.pop();
        k--;
    }
    // Step 5: build result, strip leading zeros
    StringBuilder sb = new StringBuilder();
    while (!stack.isEmpty()) {
        sb.append(stack.pollLast());
    }
    String result = sb.toString().replaceAll("^0+", "");
    return result.isEmpty() ? "0" : result;
}
```

### 📊 Complexity

- **Time: O(n)** — each digit is pushed and popped at most once.
- **Space: O(n)** — the stack holds at most n digits.

---

## 17. Decode Ways — LC 91

**Difficulty:** Medium | **Pattern:** Dynamic Programming | **Tags:** String, Dynamic Programming

### 📌 Problem Statement

A message encoded as digits ('A'=1, 'B'=2, ..., 'Z'=26). Given a string of digits, return the number of ways to decode it. '0' has no single-digit mapping.

```
Input:  "12"
Output: 2  // "AB" (1,2) or "L" (12)

Input:  "226"
Output: 3  // "BZ"(2,26), "VF"(22,6), "BBF"(2,2,6)

Input:  "06"
Output: 0  // '0' can't start a valid code
```

### 🎤 Interview Framing

EPAM interviewers care about edge cases here: `"0"`, `"10"`, `"100"`, `"30"`. Walk through them. The key: a digit or pair is valid only if it maps to 1–26. '0' alone is always invalid; '0' as the second digit is valid only as the second digit of 10 or 20.

### ❌ Brute Force

Recursion: at each position, try taking 1 digit (if valid) or 2 digits (if valid). Count all paths.
**Complexity:** Time O(2ⁿ) — exponential branching. Space O(n) recursion stack.

### 🧠 From Brute to Optimal

Overlapping subproblems: `decode(i)` depends only on `decode(i+1)` and `decode(i+2)`. Classic DP. Let `dp[i]` = number of ways to decode `s[i..n-1]`. Transition:
- If `s[i] != '0'`: add `dp[i+1]` (take 1 digit).
- If `s[i..i+1]` forms 10–26: add `dp[i+2]` (take 2 digits).
Base cases: `dp[n] = 1` (empty string = one way), `dp[n-1]` depends on last digit.

Space-optimize: we only need the last two values — use two variables.

### ✅ Optimal Solution

**Steps:**

1. Edge case: empty string or leading '0' → 0 ways.
2. Walk right to left with two variables (`dp1` = ways from i+1, `dp2` = ways from i+2).
3. At each position: if digit != '0', single decode adds `dp1`.
4. If two-digit value is 10–26, two-digit decode adds `dp2`.
5. Slide the dp window.

```java
public int numDecodings(String s) {
    int n = s.length();
    // Step 1: edge case
    if (n == 0 || s.charAt(0) == '0') {
        return 0;
    }
    // Step 2: dp1 = ways from i+1, dp2 = ways from i+2
    int dp1 = 1;
    int dp2 = 1;
    for (int i = n - 2; i >= 0; i--) {
        int current = 0;
        // Step 3: single digit decode
        if (s.charAt(i) != '0') {
            current = dp1;
        }
        // Step 4: two digit decode (10–26)
        int twoDigit = Integer.parseInt(s.substring(i, i + 2));
        if (twoDigit >= 10 && twoDigit <= 26) {
            current += dp2;
        }
        // Step 5: slide window
        dp2 = dp1;
        dp1 = current;
    }
    return dp1;
}
```

### 📊 Complexity

- **Time: O(n)** — single pass right to left over the string.
- **Space: O(1)** — two integer variables replace the full dp array.

---

## 18. Longest Substring Without Repeating Characters — LC 3

**Difficulty:** Medium | **Pattern:** Sliding Window | **Tags:** Hash Table, String, Sliding Window

### 📌 Problem Statement

Given a string `s`, find the length of the longest substring without any repeating characters.

```
Input:  "abcabcbb"
Output: 3   // "abc"

Input:  "pwwkew"
Output: 3   // "wke"
```

### 🎤 Interview Framing

The canonical sliding window problem. After solving, EPAM may ask: *"What if we want the actual substring, not just its length?"* → Track `start` index of the window. Or: *"What if characters have weights?"* → Same window, different aggregation.

### ❌ Brute Force

Generate all substrings. For each, check if it has unique characters with a HashSet.
**Complexity:** Time O(n³) — O(n²) substrings, each checked in O(n). Space O(min(n, alphabet)).

### 🧠 From Brute to Optimal

The inefficiency: when we find a duplicate at position `right`, we reset the window from scratch. Instead, remember where each character was last seen. When we see a duplicate, jump `left` to `lastSeen[char] + 1` directly — no need to re-scan.

### ✅ Optimal Solution

**Steps:**

1. Use a HashMap to store each character's most recent index.
2. Expand `right` through the string.
3. If current char was seen before and its last position ≥ `left`, jump `left` past that position.
4. Update the char's position in the map.
5. Track max window size.

```java
public int lengthOfLongestSubstring(String s) {
    // Step 1: char → last seen index
    Map<Character, Integer> lastSeen = new HashMap<>();
    int maxLen = 0;
    int left = 0;
    // Step 2: expand right
    for (int right = 0; right < s.length(); right++) {
        char c = s.charAt(right);
        // Step 3: duplicate in window? jump left past it
        if (lastSeen.containsKey(c) && lastSeen.get(c) >= left) {
            left = lastSeen.get(c) + 1;
        }
        // Step 4: update position
        lastSeen.put(c, right);
        // Step 5: track best
        maxLen = Math.max(maxLen, right - left + 1);
    }
    return maxLen;
}
```

### 📊 Complexity

- **Time: O(n)** — right pointer moves forward n times; left pointer never moves backward.
- **Space: O(min(n, |Σ|))** — HashMap stores at most one entry per unique character; |Σ| is alphabet size (26 for lowercase, 128 for ASCII).

---

## 19. Longest Palindromic Substring — LC 5

**Difficulty:** Medium | **Pattern:** Expand Around Center | **Tags:** Two Pointers, String, Dynamic Programming

### 📌 Problem Statement

Given a string `s`, return the longest palindromic substring.

```
Input:  "babad"
Output: "bab"  (or "aba" — both valid)

Input:  "cbbd"
Output: "bb"
```

### 🎤 Interview Framing

Three approaches exist: O(n³) brute, O(n²) expand-around-center, O(n) Manacher's. EPAM expects expand-around-center — mention Manacher's exists if they push for optimal, but don't implement it unless asked. Key insight to state: a palindrome "mirrors" around its center; there are 2n-1 possible centers (n single characters + n-1 gaps between characters).

### ❌ Brute Force

Check every substring. Use a helper to verify if it's a palindrome.
**Complexity:** Time O(n³). Space O(1).

### 🧠 From Brute to Optimal

For each of the 2n-1 centers, expand outward while the characters on both sides match. Track the maximum expansion. Odd-length palindromes have a single character center; even-length have a gap center.

### ✅ Optimal Solution

**Steps:**

1. For each index i, try expanding around center for both odd-length and even-length palindromes.
2. Expand outward while characters match on both sides.
3. Track the longest palindrome's start index and length.
4. Return the substring.

```java
public String longestPalindrome(String s) {
    int start = 0;
    int maxLen = 1;
    // Step 1: try each index as center
    for (int i = 0; i < s.length(); i++) {
        int len1 = expand(s, i, i);       // odd-length
        int len2 = expand(s, i, i + 1);   // even-length
        // Step 3: update best
        int len = Math.max(len1, len2);
        if (len > maxLen) {
            maxLen = len;
            start = i - (len - 1) / 2;
        }
    }
    // Step 4: extract result
    return s.substring(start, start + maxLen);
}

// Step 2: expand outward while chars match
private int expand(String s, int left, int right) {
    while (left >= 0 && right < s.length() && s.charAt(left) == s.charAt(right)) {
        left--;
        right++;
    }
    return right - left - 1;
}
```

### 📊 Complexity

- **Time: O(n²)** — n centers, each expansion takes O(n) in the worst case.
- **Space: O(1)** — only index variables; we store start/length, not the substring itself.

---

## 20. Group Anagrams — LC 49

**Difficulty:** Medium | **Pattern:** Hashable Key | **Tags:** Array, Hash Table, String

### 📌 Problem Statement

Given an array of strings, group the anagrams together. Each group can be in any order.

```
Input:  ["eat","tea","tan","ate","nat","bat"]
Output: [["bat"],["nat","tan"],["ate","eat","tea"]]
```

### 🎤 Interview Framing

Two sub-approaches for the key: (1) sort each word alphabetically — anagrams produce the same sorted key. (2) build a character-frequency signature like `"a2e1t1"`. Sorting is simpler and O(k log k) per word; frequency signature is O(k) per word but more code. EPAM expects sorted key.

### ❌ Brute Force

For every pair of strings, check if they're anagrams. Group matching pairs.
**Complexity:** Time O(n² × k log k). Messy to implement correctly.

### 🧠 From Brute to Optimal

Key insight: all anagrams of a word have the same sorted form. Use sorted form as a HashMap key. One pass: sort each word, group by sorted key.

### ✅ Optimal Solution

**Steps:**

1. For each word, sort its characters to produce a canonical key.
2. Use a HashMap: sorted key → list of original words.
3. Group each word under its sorted key.
4. Return all groups.

```java
public List<List<String>> groupAnagrams(String[] strs) {
    // Step 2: sorted key → group
    Map<String, List<String>> groups = new HashMap<>();
    for (String word : strs) {
        // Step 1: canonical key via sorting
        char[] chars = word.toCharArray();
        Arrays.sort(chars);
        String key = new String(chars);
        // Step 3: add word to its group
        groups.computeIfAbsent(key, k -> new ArrayList<>()).add(word);
    }
    // Step 4: collect all groups
    return new ArrayList<>(groups.values());
}
```

### 📊 Complexity

- **Time: O(n × k log k)** — n words, each sorted in O(k log k) where k is word length.
- **Space: O(n × k)** — the HashMap stores all words; keys add at most k characters per entry.

---

## 21. LRU Cache — LC 146

**Difficulty:** Medium | **Pattern:** HashMap + Doubly Linked List | **Tags:** Hash Table, Linked List, Design

### 📌 Problem Statement

Design a data structure for a Least Recently Used (LRU) cache (a cache that evicts the item used least recently when it's full) supporting:
- `get(key)` — return value or -1 if not exists. Marks as recently used.
- `put(key, value)` — insert/update. If at capacity, evict the LRU item first.

Both operations must run in O(1).

```
LRUCache cache = new LRUCache(2);
cache.put(1, 1);   // cache: {1=1}
cache.put(2, 2);   // cache: {1=1, 2=2}
cache.get(1);      // return 1, cache: {2=2, 1=1} (1 is now most recent)
cache.put(3, 3);   // evict 2, cache: {1=1, 3=3}
cache.get(2);      // return -1 (evicted)
```

### 🎤 Interview Framing

EPAM cares about the design reasoning, not just the code. Walk through: *"HashMap gives O(1) lookup, but eviction requires knowing which item was used least recently — that's ordering. A doubly linked list gives O(1) insert/delete if we have a pointer. Combine them: HashMap maps key → node in the DLL. DLL maintains access order."*

### ❌ Brute Force

Use a LinkedHashMap and override `removeEldestEntry`.
**Complexity:** O(1) — but this is cheating the design question.

### 🧠 From Brute to Optimal

The "real" implementation: dummy head + dummy tail nodes for the DLL to avoid null checks. Head is always the most recently used; tail is always the LRU. On access: remove the node from its current position, insert after head. On eviction: remove the node before tail.

### ✅ Optimal Solution

**Steps:**

1. Use a HashMap (key → DLL node) for O(1) lookup.
2. Use a doubly linked list with dummy head/tail for O(1) insert/remove.
3. `get`: lookup in map, move node to front (most recent), return value.
4. `put`: if key exists → update value, move to front. If new → evict LRU (tail.prev) if at capacity, then insert after head.

```java
class LRUCache {
    private class Node {
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
    // Step 1: O(1) lookup
    private final Map<Integer, Node> map;
    // Step 2: dummy head/tail for clean insert/remove
    private final Node head;
    private final Node tail;

    public LRUCache(int capacity) {
        this.capacity = capacity;
        this.map = new HashMap<>();
        head = new Node(0, 0);
        tail = new Node(0, 0);
        head.next = tail;
        tail.prev = head;
    }

    // Step 3: get = lookup + move to front
    public int get(int key) {
        if (!map.containsKey(key)) {
            return -1;
        }
        Node node = map.get(key);
        moveToFront(node);
        return node.val;
    }

    // Step 4: put = update or insert (evict if full)
    public void put(int key, int value) {
        if (map.containsKey(key)) {
            Node node = map.get(key);
            node.val = value;
            moveToFront(node);
        } else {
            // Evict LRU if at capacity
            if (map.size() == capacity) {
                Node lru = tail.prev;
                remove(lru);
                map.remove(lru.key);
            }
            Node newNode = new Node(key, value);
            map.put(key, newNode);
            insertAfterHead(newNode);
        }
    }

    private void remove(Node node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    private void insertAfterHead(Node node) {
        node.next = head.next;
        node.prev = head;
        head.next.prev = node;
        head.next = node;
    }

    private void moveToFront(Node node) {
        remove(node);
        insertAfterHead(node);
    }
}
```

### 📊 Complexity

- **Time: O(1)** for both `get` and `put` — HashMap lookup is O(1), DLL insert/remove is O(1) with direct node pointers.
- **Space: O(capacity)** — HashMap and DLL each hold at most `capacity` nodes.

---

## 22. Add Two Numbers — LC 2

**Difficulty:** Medium | **Pattern:** Linked List Simulation | **Tags:** Linked List, Math, Recursion

### 📌 Problem Statement

Two non-empty linked lists represent two non-negative integers stored in reverse order (head is the ones digit). Add them and return the sum as a linked list in reverse order.

```
Input:  2→4→3  and  5→6→4
        (represents 342 + 465)
Output: 7→0→8   (represents 807)
```

### 🎤 Interview Framing

This is grade-school addition simulated on linked lists. The challenge is carry propagation and handling lists of different lengths. EPAM wants to see clean carry management and not panicking when one list runs out before the other.

### ❌ Brute Force

Convert both lists to integers, add them, convert back to a list. Fails for very large numbers (overflow beyond Long range).

### 🧠 From Brute to Optimal

Simulate digit-by-digit addition. Walk both lists simultaneously. At each step: `sum = l1.val + l2.val + carry`. New node gets `sum % 10`. `carry = sum / 10`. Advance whichever list pointer is non-null. After both lists are exhausted, if carry > 0, append one more node.

### ✅ Optimal Solution

**Steps:**

1. Create a dummy head node for the result list.
2. Walk both lists simultaneously, adding digits + carry.
3. New digit = `sum % 10`, carry = `sum / 10`.
4. Continue until both lists exhausted AND carry is 0.
5. Return `dummy.next`.

```java
public ListNode addTwoNumbers(ListNode l1, ListNode l2) {
    // Step 1: dummy head avoids null-check for first node
    ListNode dummy = new ListNode(0);
    ListNode current = dummy;
    int carry = 0;
    // Step 4: loop until both lists done and no carry
    while (l1 != null || l2 != null || carry != 0) {
        // Step 2: add available digits + carry
        int sum = carry;
        if (l1 != null) {
            sum += l1.val;
            l1 = l1.next;
        }
        if (l2 != null) {
            sum += l2.val;
            l2 = l2.next;
        }
        // Step 3: extract digit and carry
        carry = sum / 10;
        current.next = new ListNode(sum % 10);
        current = current.next;
    }
    // Step 5: skip dummy
    return dummy.next;
}
```

### 📊 Complexity

- **Time: O(max(m, n))** — m and n are lengths of the two lists; we traverse both fully, plus one extra step if there's a final carry.
- **Space: O(max(m, n) + 1)** — the result list has at most max(m,n)+1 nodes (one extra for carry).

---

## 23. 3Sum — LC 15

**Difficulty:** Medium | **Pattern:** Sort + Two Pointers | **Tags:** Array, Two Pointers, Sorting

### 📌 Problem Statement

Given an integer array `nums`, return all unique triplets `[nums[i], nums[j], nums[k]]` such that `i`, `j`, `k` are distinct and their sum is 0.

```
Input:  [-1, 0, 1, 2, -1, -4]
Output: [[-1,-1,2], [-1,0,1]]
```

### 🎤 Interview Framing

EPAM's main concern: **no duplicate triplets**. Walk through how sorting + skipping duplicates handles this. The follow-up is often 4Sum (LC 18) — just add one more outer loop. State that clearly.

### ❌ Brute Force

Three nested loops. Check all O(n³) triplets. Use a HashSet to deduplicate.
**Complexity:** Time O(n³). Space O(n) for the set.

### 🧠 From Brute to Optimal

Fix one element with an outer loop (index `i`). The subproblem becomes: find two numbers in `nums[i+1..n-1]` that sum to `-nums[i]`. That's Two Sum on a sorted subarray — solvable in O(n) with two pointers. Sort first to enable this AND to make duplicate-skipping easy (equal values are adjacent).

### ✅ Optimal Solution

**Steps:**

1. Sort the array (enables two-pointer + duplicate skipping).
2. Fix first element with outer loop; skip duplicates for it.
3. Two pointers (`left`, `right`) find pairs summing to `−nums[i]`.
4. On match: record triplet, skip duplicates for both pointers, then move both.
5. If sum too small → move left; too large → move right.

```java
public List<List<Integer>> threeSum(int[] nums) {
    // Step 1: sort
    Arrays.sort(nums);
    List<List<Integer>> result = new ArrayList<>();
    // Step 2: fix first element
    for (int i = 0; i < nums.length - 2; i++) {
        if (i > 0 && nums[i] == nums[i - 1]) {
            continue;  // skip duplicate first element
        }
        // Step 3: two pointers for remaining pair
        int left = i + 1;
        int right = nums.length - 1;
        while (left < right) {
            int sum = nums[i] + nums[left] + nums[right];
            if (sum == 0) {
                // Step 4: found triplet → skip duplicates
                result.add(Arrays.asList(nums[i], nums[left], nums[right]));
                while (left < right && nums[left] == nums[left + 1]) {
                    left++;
                }
                while (left < right && nums[right] == nums[right - 1]) {
                    right--;
                }
                left++;
                right--;
            // Step 5: adjust pointers
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

### 📊 Complexity

- **Time: O(n²)** — sorting is O(n log n); the outer loop runs n times, and the two-pointer inner loop is O(n) per iteration → O(n²) dominates.
- **Space: O(log n)** — sorting in-place; result list not counted as auxiliary space.

---

## 24. Minimum Operations to Make a Uni-Value Grid — LC 2033

**Difficulty:** Medium | **Pattern:** Median, Sorting | **Tags:** Array, Math, Sorting

### 📌 Problem Statement

Given a 2D grid and an integer `x`. In one operation, add or subtract `x` from any cell. Return the minimum operations to make all cells equal, or -1 if impossible.

```
grid = [[2,4],[6,8]], x = 2
Output: 4
// All cells to value 4: 2+2=4, 6-2=4, 8-4=4 → operations: 1+1+2 = 4
```

### 🎤 Interview Framing

Two key insights to state upfront: (1) All cells must have the same remainder when divided by x — otherwise impossible. (2) The optimal target value is the **median** of all cell values (same logic as the "minimum total distance" classic — the median minimizes sum of absolute deviations).

### ❌ Brute Force

Try all possible target values. For each, compute total operations. Return minimum.
**Complexity:** Time O(n²) where n = total cells. Space O(n).

### 🧠 From Brute to Optimal

Flatten the grid, sort it. Check all values have the same `% x` — if not, return -1. Then: the optimal target is the median. Sum up `|cell - median| / x` for all cells.

### ✅ Optimal Solution

**Steps:**

1. Flatten the 2D grid into a 1D array and sort it.
2. Verify all values have the same `% x` remainder — if not, return −1.
3. Pick the median (minimizes sum of absolute deviations).
4. Sum up `|cell − median| / x` for all cells.

```java
public int minOperations(int[][] grid, int x) {
    int m = grid.length;
    int n = grid[0].length;
    // Step 1: flatten + sort
    int[] flat = new int[m * n];
    int idx = 0;
    for (int[] row : grid) {
        for (int val : row) {
            flat[idx++] = val;
        }
    }
    Arrays.sort(flat);
    // Step 2: same remainder check
    int remainder = flat[0] % x;
    for (int val : flat) {
        if (val % x != remainder) {
            return -1;
        }
    }
    // Step 3: target = median
    int median = flat[flat.length / 2];
    // Step 4: count total operations
    int ops = 0;
    for (int val : flat) {
        ops += Math.abs(val - median) / x;
    }
    return ops;
}
```

### 📊 Complexity

- **Time: O(n log n)** — sorting n = m×c cells dominates; everything else is O(n).
- **Space: O(n)** — the flat array of all cell values.

---

## 25. Asteroid Collision — LC 735

**Difficulty:** Medium | **Pattern:** Stack Simulation | **Tags:** Array, Stack, Simulation

### 📌 Problem Statement

An array of integers represents asteroids. The absolute value is size; positive = moving right, negative = moving left. Asteroids moving the same direction never collide. When they collide, the smaller one explodes; if equal size, both explode. Return the final state.

```
Input:  [5, 10, -5]
Output: [5, 10]   // -5 hits 10 → 10 survives

Input:  [8, -8]
Output: []        // both explode

Input:  [10, 2, -5]
Output: [10]      // -5 destroys 2, then hits 10 → -5 loses
```

### 🎤 Interview Framing

"What's on the stack at any point?" — only right-moving asteroids that haven't been destroyed yet (or left-moving ones that have safely passed everything). Collision only happens when a right-mover is on the stack and a left-mover arrives. Enumerate all sub-cases: left-mover larger, right-mover larger, equal.

### ❌ Brute Force

Simulate repeatedly scanning the array and removing colliding pairs until stable.
**Complexity:** Time O(n²) in the worst case (each scan removes one pair).

### 🧠 From Brute to Optimal

Process left to right. Stack holds "survivors so far." When we see a negative asteroid, it only collides with the top of the stack if the top is positive. Handle the collision loop: while stack top is positive and smaller than the incoming asteroid, pop it. If they're equal, pop and discard both. If top is larger, discard incoming. If stack is empty or top is negative, push incoming.

### ✅ Optimal Solution

**Steps:**

1. Process asteroids left to right, pushing onto a stack.
2. Collision only when incoming is negative and stack top is positive.
3. Compare sizes: top smaller → pop it, continue loop. Equal → pop both. Top larger → discard incoming.
4. If no collision, push the asteroid.
5. Convert stack to result array.

```java
public int[] asteroidCollision(int[] asteroids) {
    // Step 1: stack holds survivors
    Deque<Integer> stack = new ArrayDeque<>();
    for (int asteroid : asteroids) {
        boolean destroyed = false;
        // Step 2: collision? (negative incoming vs positive top)
        while (!stack.isEmpty() && asteroid < 0 && stack.peek() > 0) {
            // Step 3: compare sizes
            if (stack.peek() < -asteroid) {
                stack.pop();    // top destroyed, keep checking
            } else if (stack.peek() == -asteroid) {
                stack.pop();    // both destroyed
                destroyed = true;
                break;
            } else {
                destroyed = true;  // incoming destroyed
                break;
            }
        }
        // Step 4: survived all collisions → push
        if (!destroyed) {
            stack.push(asteroid);
        }
    }
    // Step 5: stack → array
    int[] result = new int[stack.size()];
    for (int i = result.length - 1; i >= 0; i--) {
        result[i] = stack.pop();
    }
    return result;
}
```

### 📊 Complexity

- **Time: O(n)** — each asteroid is pushed and popped at most once.
- **Space: O(n)** — the stack holds at most all n asteroids.

---

## 26. Coin Change — LC 322

**Difficulty:** Medium | **Pattern:** Unbounded Knapsack DP | **Tags:** Array, Dynamic Programming, BFS

### 📌 Problem Statement

Given coin denominations and an `amount`, return the fewest number of coins needed to make up that amount. Return -1 if not possible.

```
coins = [1, 5, 11], amount = 15
Output: 3  // 5+5+5

coins = [2], amount = 3
Output: -1
```

### 🎤 Interview Framing

The greedy approach (always take the largest coin) fails — classic counterexample: coins `[1, 3, 4]`, amount `6`. Greedy picks 4+1+1 = 3 coins. Optimal is 3+3 = 2 coins. This is why DP is needed. State this counterexample proactively.

### ❌ Brute Force

Recursion: at each step try every coin. Return min over all choices.
**Complexity:** Time O(amount^n) — exponential. Massive overlapping subproblems.

### 🧠 From Brute to Optimal

`dp[i]` = minimum coins to make amount `i`. Transition: for each coin `c`, if `i >= c` then `dp[i] = min(dp[i], dp[i - c] + 1)`. Base case: `dp[0] = 0`. Fill bottom-up from 1 to amount.

Why does this work? `dp[i - c]` is already computed (smaller subproblem). We just check: "if we use coin c as the last coin, how many did we need for the rest?"

### ✅ Optimal Solution

**Steps:**

1. Create dp array of size amount+1, fill with `amount + 1` (impossible sentinel).
2. Base case: `dp[0] = 0` (zero coins needed for amount 0).
3. For each amount `i` from 1 to target: try every coin `c`.
4. If `c ≤ i`, take `dp[i] = min(dp[i], dp[i - c] + 1)`.
5. If `dp[amount]` is still the sentinel, return −1.

```java
public int coinChange(int[] coins, int amount) {
    // Step 1: sentinel = impossible value
    int[] dp = new int[amount + 1];
    Arrays.fill(dp, amount + 1);
    // Step 2: base case
    dp[0] = 0;
    // Step 3: fill bottom-up
    for (int i = 1; i <= amount; i++) {
        for (int coin : coins) {
            // Step 4: can we use this coin?
            if (coin <= i) {
                dp[i] = Math.min(dp[i], dp[i - coin] + 1);
            }
        }
    }
    // Step 5: check if reachable
    return dp[amount] > amount ? -1 : dp[amount];
}
```

### 📊 Complexity

- **Time: O(amount × n)** — outer loop runs `amount` times; inner loop runs `n` times (number of coin types).
- **Space: O(amount)** — the dp array of size amount+1.

---

## 27. House Robber — LC 198

**Difficulty:** Medium | **Pattern:** Linear DP | **Tags:** Array, Dynamic Programming

### 📌 Problem Statement

You are a robber. An array of non-negative integers represents the amount of money in each house. You cannot rob two adjacent houses (the alarm triggers). Maximize total money robbed.

```
Input:  [2, 7, 9, 3, 1]
Output: 12   // Rob houses 1, 3, 5 → 2+9+1=12
```

### 🎤 Interview Framing

EPAM often extends this: *"What if the houses are in a circle?"* (LC 213 — run the algorithm twice: once skipping first house, once skipping last; take max). Know this extension cold.

### ❌ Brute Force

Recursion: at each house, either rob it (skip next) or skip it. Try all combinations.
**Complexity:** Time O(2ⁿ). Space O(n) stack.

### 🧠 From Brute to Optimal

At each house i, the choice is: rob house i (get `nums[i] + dp[i-2]`) or skip it (get `dp[i-1]`). Take the max. Only need the previous two values — space-optimize to O(1).

### ✅ Optimal Solution

**Steps:**

1. Edge case: single house → return its value.
2. `prev2` = max loot up to house i−2; `prev1` = max loot up to house i−1.
3. At each house i: choose `max(skip it = prev1, rob it = prev2 + nums[i])`.
4. Slide the window forward.

```java
public int rob(int[] nums) {
    // Step 1: single house
    if (nums.length == 1) {
        return nums[0];
    }
    // Step 2: init two previous states
    int prev2 = nums[0];
    int prev1 = Math.max(nums[0], nums[1]);
    for (int i = 2; i < nums.length; i++) {
        // Step 3: rob or skip?
        int current = Math.max(prev1, prev2 + nums[i]);
        // Step 4: slide forward
        prev2 = prev1;
        prev1 = current;
    }
    return prev1;
}
```

### 📊 Complexity

- **Time: O(n)** — single pass through the array.
- **Space: O(1)** — two scalar variables replace the full dp array.

---

## 28. Partition Equal Subset Sum — LC 416

**Difficulty:** Medium | **Pattern:** 0/1 Knapsack DP | **Tags:** Array, Dynamic Programming

### 📌 Problem Statement

Given a non-empty integer array `nums`, return `true` if it can be partitioned into two subsets with equal sum.

```
Input:  [1, 5, 11, 5]
Output: true   // [1, 5, 5] and [11]

Input:  [1, 2, 3, 5]
Output: false
```

### 🎤 Interview Framing

Reframe: *"Can any subset sum to `totalSum / 2`?"* If `totalSum` is odd, immediately return false. This is the classic 0/1 knapsack problem disguised — each element can be included or excluded.

### ❌ Brute Force

Recursion: include or exclude each number, check if any path sums to `target = totalSum / 2`.
**Complexity:** Time O(2ⁿ). Space O(n).

### 🧠 From Brute to Optimal

`dp[j]` = can we achieve sum `j` using some subset of the numbers seen so far? Transition (iterate backwards to avoid using the same element twice): for each number `num`, for `j` from `target` down to `num`: `dp[j] = dp[j] || dp[j - num]`. Start with `dp[0] = true`.

### ✅ Optimal Solution

**Steps:**

1. Compute total sum. If odd → impossible, return false.
2. Target = totalSum / 2.
3. `dp[j]` = can we form sum `j`? Base: `dp[0] = true`.
4. For each number, iterate `j` from target down to `num` (backward to avoid reuse).
5. `dp[j] = dp[j] || dp[j − num]`.

```java
public boolean canPartition(int[] nums) {
    // Step 1: total sum
    int total = 0;
    for (int num : nums) {
        total += num;
    }
    if (total % 2 != 0) {
        return false;
    }
    // Step 2: target = half
    int target = total / 2;
    // Step 3: dp[j] = can we make sum j?
    boolean[] dp = new boolean[target + 1];
    dp[0] = true;
    for (int num : nums) {
        // Step 4–5: backwards to prevent reuse
        for (int j = target; j >= num; j--) {
            dp[j] = dp[j] || dp[j - num];
        }
    }
    return dp[target];
}
```

### 📊 Complexity

- **Time: O(n × target)** — n numbers, each updates target slots in the dp array.
- **Space: O(target)** — 1D boolean dp array of size target+1.

---

## 29. Maximum Difference Between Node and Ancestor — LC 1026

**Difficulty:** Medium | **Pattern:** Tree DFS | **Tags:** Tree, Depth-First Search, Binary Tree

### 📌 Problem Statement

Given a binary tree, find the maximum value of `|node.val - ancestor.val|` for any node and any of its ancestors.

```
        8
       / \
      3   10
     / \    \
    1   6    14
       / \   /
      4   7  13

Output: 7  // |8 - 1| = 7
```

### 🎤 Interview Framing

The key insight: for a given node, the maximum absolute difference with any ancestor equals `max(nodeVal - minAncestor, maxAncestor - nodeVal)`. So track the min and max values seen on the path from root to current node.

### ❌ Brute Force

For each node, walk up to root and compare with every ancestor.
**Complexity:** Time O(n²). Space O(n).

### 🧠 From Brute to Optimal

DFS carrying the running min and max from the root. At each node, update the global answer with `max(node.val - currentMin, currentMax - node.val)`. Recurse with updated min/max. No parent pointers needed.

### ✅ Optimal Solution

**Steps:**

1. DFS from root, carrying the running min and max of all ancestors.
2. At each node, update min/max with current node's value.
3. At leaf (null), the answer for this path = maxVal − minVal.
4. Return the max across left and right subtrees.

```java
public int maxAncestorDiff(TreeNode root) {
    // Step 1: start DFS with root as both min and max
    return dfs(root, root.val, root.val);
}

private int dfs(TreeNode node, int minVal, int maxVal) {
    // Step 3: leaf → compute max diff for this root-to-leaf path
    if (node == null) {
        return maxVal - minVal;
    }
    // Step 2: update running min/max
    minVal = Math.min(minVal, node.val);
    maxVal = Math.max(maxVal, node.val);
    // Step 4: best of left and right subtrees
    int left = dfs(node.left, minVal, maxVal);
    int right = dfs(node.right, minVal, maxVal);
    return Math.max(left, right);
}
```

### 📊 Complexity

- **Time: O(n)** — visit every node exactly once.
- **Space: O(h)** — recursion stack depth equals tree height h; O(log n) balanced, O(n) skewed.

---

## 30. Reverse Integer — LC 7

**Difficulty:** Medium | **Pattern:** Math | **Tags:** Math

### 📌 Problem Statement

Given a signed 32-bit integer `x`, return `x` with its digits reversed. If reversing causes overflow (outside [-2³¹, 2³¹-1]), return 0.

```
123   → 321
-123  → -321
120   → 21
```

### 🎤 Interview Framing

The overflow check is the entire point. Don't just reverse digits — detect overflow *before* it happens. The clean way: build the reversed number one digit at a time, check overflow before each multiply-and-add step.

### ❌ Brute Force

Convert to string, reverse, convert back to integer with `Integer.parseInt`. Surround with try-catch for overflow.
Works but: (1) not pure math, (2) exceptions for control flow is bad practice.

### 🧠 From Brute to Optimal

Extract digits with `x % 10`, build reversed number with `rev = rev * 10 + digit`. Before each step, check if `rev > Integer.MAX_VALUE / 10` or `rev < Integer.MIN_VALUE / 10` to catch overflow before it occurs.

### ✅ Optimal Solution

**Steps:**

1. Extract the last digit with `x % 10`, shrink x with `x / 10`.
2. Before appending digit to `rev`: check overflow against MAX_VALUE/10 and MIN_VALUE/10.
3. Build reversed number: `rev = rev * 10 + digit`.
4. Repeat until x == 0.

```java
public int reverse(int x) {
    int rev = 0;
    while (x != 0) {
        // Step 1: extract last digit
        int digit = x % 10;
        x /= 10;
        // Step 2: overflow check BEFORE multiply
        if (rev > Integer.MAX_VALUE / 10 || (rev == Integer.MAX_VALUE / 10 && digit > 7)) {
            return 0;
        }
        if (rev < Integer.MIN_VALUE / 10 || (rev == Integer.MIN_VALUE / 10 && digit < -8)) {
            return 0;
        }
        // Step 3: append digit
        rev = rev * 10 + digit;
    }
    return rev;
}
```

Note: `Integer.MAX_VALUE = 2147483647` (ends in 7); `Integer.MIN_VALUE = -2147483648` (ends in 8). That's where `> 7` and `< -8` come from.

### 📊 Complexity

- **Time: O(log₁₀ x)** — number of digits in x.
- **Space: O(1)** — only integer variables.

---

## 31. Longest Consecutive Sequence — LC 128

**Difficulty:** Medium | **Pattern:** HashSet | **Tags:** Array, Hash Table, Union Find

### 📌 Problem Statement

Given an unsorted integer array `nums`, return the length of the longest consecutive sequence of integers. Must run in O(n).

```
Input:  [100, 4, 200, 1, 3, 2]
Output: 4   // [1, 2, 3, 4]
```

### 🎤 Interview Framing

The "must be O(n)" constraint rules out sorting (O(n log n)). EPAM wants to see the HashSet trick. Key insight: only start counting from the *beginning* of a sequence — i.e., from numbers where `num - 1` is NOT in the set.

### ❌ Brute Force

Sort the array. Scan for consecutive runs, tracking max length.
**Complexity:** Time O(n log n). Space O(1). Valid but doesn't meet the O(n) requirement.

### 🧠 From Brute to Optimal

Put all numbers in a HashSet for O(1) lookup. For each number, check if it's a sequence start (`num - 1` not in set). If yes, count forward as long as consecutive numbers exist. This looks O(n²) but is actually O(n) — each number is counted at most once across all sequences (each number is a "start" at most once and a "continuation" at most once).

### ✅ Optimal Solution

**Steps:**

1. Put all numbers into a HashSet for O(1) lookup.
2. For each number: check if it's a sequence start (`num − 1` not in set).
3. If it is a start, count forward consecutively.
4. Track max sequence length.

```java
public int longestConsecutive(int[] nums) {
    // Step 1: O(1) lookup
    Set<Integer> numSet = new HashSet<>();
    for (int num : nums) {
        numSet.add(num);
    }
    int maxLen = 0;
    for (int num : numSet) {
        // Step 2: only start from the beginning of a sequence
        if (!numSet.contains(num - 1)) {
            // Step 3: count consecutive
            int currentNum = num;
            int currentLen = 1;
            while (numSet.contains(currentNum + 1)) {
                currentNum++;
                currentLen++;
            }
            // Step 4: update best
            maxLen = Math.max(maxLen, currentLen);
        }
    }
    return maxLen;
}
```

### 📊 Complexity

- **Time: O(n)** — each number is added to the set once (O(n)). The while loop across all iterations together visits each number at most once as a sequence continuation → O(n) total.
- **Space: O(n)** — the HashSet stores all n elements.

---

## 32. String Compression — LC 443

**Difficulty:** Medium | **Pattern:** Two Pointers | **Tags:** Two Pointers, String

### 📌 Problem Statement

Compress a character array in-place: replace runs of repeated characters with the character followed by the count. Count 1 is omitted. Modify the array in-place and return the new length.

```
Input:  ['a','a','b','b','c','c','c']
Output: 6, array = ['a','2','b','2','c','3']

Input:  ['a']
Output: 1, array = ['a']
```

### 🎤 Interview Framing

In-place + write position pointer. The tricky part: counts > 9 require multiple characters (e.g., count 12 → write '1', '2'). Use a StringBuilder or extract digits. EPAM wants clean handling of multi-digit counts.

### ❌ Brute Force

Use a separate string/list. Build the compressed form. Copy back.
**Complexity:** Time O(n). Space O(n).

### 🧠 From Brute to Optimal

Two pointers: `read` scans through the original array, `write` tracks where to write compressed output. For each group of identical characters, write the character, then write the count (if > 1) digit by digit.

### ✅ Optimal Solution

**Steps:**

1. Two pointers: `read` scans groups, `write` tracks output position.
2. For each group: count consecutive identical characters.
3. Write the character at `write`.
4. If count > 1, write the digits of the count (handles multi-digit counts).
5. Return `write` as the new length.

```java
public int compress(char[] chars) {
    // Step 1: read scans, write outputs
    int write = 0;
    int read = 0;
    while (read < chars.length) {
        char currentChar = chars[read];
        // Step 2: count the group
        int count = 0;
        while (read < chars.length && chars[read] == currentChar) {
            read++;
            count++;
        }
        // Step 3: write the character
        chars[write++] = currentChar;
        // Step 4: write count digits if > 1
        if (count > 1) {
            String countStr = Integer.toString(count);
            for (char c : countStr.toCharArray()) {
                chars[write++] = c;
            }
        }
    }
    // Step 5: new length
    return write;
}
```

### 📊 Complexity

- **Time: O(n)** — read pointer traverses the array once; write pointer moves at most n steps.
- **Space: O(1)** — only pointer variables (the count string is bounded by log₁₀(n) ≈ very small).

---

## 33. Rotate Array — LC 189

**Difficulty:** Medium | **Pattern:** Reverse trick | **Tags:** Array, Math, Two Pointers

### 📌 Problem Statement

Rotate array `nums` to the right by `k` steps, in-place.

```
Input:  nums = [1,2,3,4,5,6,7], k = 3
Output: [5,6,7,1,2,3,4]
```

### 🎤 Interview Framing

Three approaches exist: O(n) extra space (copy), cyclic replacements (tricky), or the **reverse trick** (elegant, O(1) space). EPAM wants the reverse trick. Also: always do `k = k % n` first — k may be larger than n.

### ❌ Brute Force

Rotate by 1, k times. Or copy the last k elements to the front.
**Complexity:** O(n×k) or O(n) space.

### 🧠 From Brute to Optimal

Reverse trick: rotating right by k is equivalent to:
1. Reverse the entire array.
2. Reverse the first k elements.
3. Reverse the remaining n-k elements.

Proof by example: `[1,2,3,4,5,6,7]`, k=3 → reverse all → `[7,6,5,4,3,2,1]` → reverse first 3 → `[5,6,7,4,3,2,1]` → reverse last 4 → `[5,6,7,1,2,3,4]` ✓

### ✅ Optimal Solution

**Steps:**

1. Normalize k: `k = k % n` (handles k > array length).
2. Reverse the entire array.
3. Reverse the first k elements (these become the rotated tail).
4. Reverse the remaining n−k elements (these become the rotated head).

```java
public void rotate(int[] nums, int k) {
    // Step 1: normalize
    k = k % nums.length;
    // Step 2: reverse all
    reverse(nums, 0, nums.length - 1);
    // Step 3: reverse first k
    reverse(nums, 0, k - 1);
    // Step 4: reverse remaining n-k
    reverse(nums, k, nums.length - 1);
}

private void reverse(int[] nums, int left, int right) {
    while (left < right) {
        int temp = nums[left];
        nums[left] = nums[right];
        nums[right] = temp;
        left++;
        right--;
    }
}
```

### 📊 Complexity

- **Time: O(n)** — three reversals, each O(n/2); total = O(n).
- **Space: O(1)** — in-place swaps.

---

## 34. Two Sum II - Input Array Is Sorted — LC 167

**Difficulty:** Medium | **Pattern:** Two Pointers | **Tags:** Array, Two Pointers, Binary Search

### 📌 Problem Statement

Given a 1-indexed sorted array `numbers` and a `target`, return the indices of two numbers that add up to target. Exactly one solution. Use only O(1) extra space.

```
Input:  numbers = [2, 7, 11, 15], target = 9
Output: [1, 2]   // numbers[1] + numbers[2] = 2 + 7 = 9
```

### 🎤 Interview Framing

This is the "why sort matters" companion to Two Sum. Because the array is sorted, we can use two pointers instead of a HashMap — achieving O(1) space. EPAM uses this to test whether you exploit the sorted precondition.

### ❌ Brute Force

Two nested loops. O(n²) time, O(1) space. Ignores the sorted property.

### 🧠 From Brute to Optimal

Left pointer at start, right pointer at end. If their sum equals target, done. If sum is too small, move left right (increase sum). If sum is too large, move right left (decrease sum). Because array is sorted, we're guaranteed to converge on the answer.

### ✅ Optimal Solution

**Steps:**

1. Set `left` at start, `right` at end of sorted array.
2. Compute sum at both pointers.
3. If sum == target → return (1-indexed). If too small → move left. If too large → move right.

```java
public int[] twoSum(int[] numbers, int target) {
    // Step 1: two pointers from both ends
    int left = 0;
    int right = numbers.length - 1;
    while (left < right) {
        // Step 2: compute sum
        int sum = numbers[left] + numbers[right];
        // Step 3: adjust
        if (sum == target) {
            return new int[]{ left + 1, right + 1 };  // 1-indexed
        } else if (sum < target) {
            left++;   // need bigger sum
        } else {
            right--;  // need smaller sum
        }
    }
    return new int[]{};
}
```

### 📊 Complexity

- **Time: O(n)** — two pointers together traverse the array at most once.
- **Space: O(1)** — only two pointer variables.

---

## 35. Pow(x, n) — LC 50

**Difficulty:** Medium | **Pattern:** Fast Exponentiation (Divide and Conquer) | **Tags:** Math, Recursion

### 📌 Problem Statement

Implement `pow(x, n)` — raise `x` to the power `n`. `n` can be negative.

```
2.0 ^ 10  → 1024.0
2.1 ^ 3   → 9.261...
2.0 ^ -2  → 0.25
```

### 🎤 Interview Framing

The naive loop is O(n) and will TLE for large n. The key insight: `x^n = x^(n/2) × x^(n/2)`. We only compute half the problem recursively, then square it — O(log n). Handle negative n: `x^(-n) = 1 / x^n`. Handle overflow: `n = Integer.MIN_VALUE` cannot be negated directly in Java.

### ❌ Brute Force

Multiply x by itself n times.
**Complexity:** Time O(n). TLEs for n = 2³¹.

### 🧠 From Brute to Optimal

Divide and conquer: if n is even, `pow(x, n) = pow(x, n/2)²`. If n is odd, `pow(x, n) = x × pow(x, n-1)`. This halves the problem each time → O(log n).

### ✅ Optimal Solution

**Steps:**

1. Handle negative exponent: invert base, negate n (use long to avoid MIN_VALUE overflow).
2. Base case: n == 0 → return 1.0.
3. If n is even: compute half = pow(x, n/2), return half × half.
4. If n is odd: return x × pow(x, n−1).

```java
public double myPow(double x, int n) {
    // Step 1: handle negative exponent (use long for MIN_VALUE safety)
    long N = n;
    if (N < 0) {
        x = 1.0 / x;
        N = -N;
    }
    return fastPow(x, N);
}

private double fastPow(double x, long n) {
    // Step 2: base case
    if (n == 0) {
        return 1.0;
    }
    // Step 3: even → square the half
    if (n % 2 == 0) {
        double half = fastPow(x, n / 2);
        return half * half;
    }
    // Step 4: odd → x × smaller problem
    return x * fastPow(x, n - 1);
}
```

### 📊 Complexity

- **Time: O(log n)** — each recursive call halves n.
- **Space: O(log n)** — recursion stack depth equals log₂(n).

---

## 36. Merge Intervals — LC 56

**Difficulty:** Medium | **Pattern:** Sort + Greedy | **Tags:** Array, Sorting

### 📌 Problem Statement

Given a list of intervals, merge all overlapping intervals and return the result.

```
Input:  [[1,3],[2,6],[8,10],[15,18]]
Output: [[1,6],[8,10],[15,18]]

Input:  [[1,4],[4,5]]
Output: [[1,5]]   // touching counts as overlapping
```

### 🎤 Interview Framing

After sorting, two intervals overlap iff `nextStart <= currentEnd`. Merge by extending `currentEnd = max(currentEnd, nextEnd)`. The sort is the key enabler — without it you'd need O(n²) pairwise comparison.

### ❌ Brute Force

For every interval, check all others for overlap. Merge if overlapping. Repeat until no merges.
**Complexity:** Time O(n²) or worse.

### 🧠 From Brute to Optimal

Sort by start time. Then one linear pass: maintain the "current merged interval." For each new interval: if its start ≤ current end → overlap → extend current end. Otherwise → no overlap → save current, start a new one.

### ✅ Optimal Solution

**Steps:**

1. Sort intervals by start time.
2. Keep a `current` merged interval.
3. For each next interval: if overlapping (start ≤ current end) → extend current end.
4. If not overlapping → save current, start a new one.
5. Don't forget to add the last `current` interval.

```java
public int[][] merge(int[][] intervals) {
    // Step 1: sort by start
    Arrays.sort(intervals, (a, b) -> a[0] - b[0]);
    List<int[]> merged = new ArrayList<>();
    // Step 2: track current merged interval
    int[] current = intervals[0];
    for (int i = 1; i < intervals.length; i++) {
        // Step 3: overlapping → extend
        if (intervals[i][0] <= current[1]) {
            current[1] = Math.max(current[1], intervals[i][1]);
        } else {
            // Step 4: gap → save and start new
            merged.add(current);
            current = intervals[i];
        }
    }
    // Step 5: add last interval
    merged.add(current);
    return merged.toArray(new int[0][]);
}
```

### 📊 Complexity

- **Time: O(n log n)** — dominated by sorting; the merge scan is O(n).
- **Space: O(n)** — the output list holds at most n intervals.

---

## 37. Generate Parentheses — LC 22

**Difficulty:** Medium | **Pattern:** Backtracking | **Tags:** String, Dynamic Programming, Backtracking

### 📌 Problem Statement

Given `n` pairs of parentheses, generate all combinations of well-formed parentheses.

```
Input:  n = 3
Output: ["((()))","(()())","(())()","()(())","()()()"]
```

### 🎤 Interview Framing

The pruning conditions are what make this clean: at any point, `open < n` means we can add `(`, and `close < open` means we can add `)`. These two conditions together guarantee all generated strings are valid — no invalid paths are explored.

### ❌ Brute Force

Generate all 2^(2n) strings of length 2n. Filter for valid parentheses.
**Complexity:** Time O(2^(2n) × n) for generation + validation. Extremely wasteful.

### 🧠 From Brute to Optimal

Backtracking with pruning. State: current string, count of open brackets used, count of close brackets used. Add `(` if `open < n`. Add `)` if `close < open`. When `open == close == n`, we have a valid complete string.

### ✅ Optimal Solution

**Steps:**

1. Backtrack with state: current string, count of open and close brackets used.
2. Base case: length == 2n → add to result.
3. If `open < n` → we can add `(`.
4. If `close < open` → we can add `)` (ensures validity).
5. After each recursive call, undo the last character (backtrack).

```java
public List<String> generateParenthesis(int n) {
    List<String> result = new ArrayList<>();
    backtrack(result, new StringBuilder(), 0, 0, n);
    return result;
}

private void backtrack(List<String> result, StringBuilder current, int open, int close, int n) {
    // Step 2: complete string → add to result
    if (current.length() == 2 * n) {
        result.add(current.toString());
        return;
    }
    // Step 3: can add '('?
    if (open < n) {
        current.append('(');
        backtrack(result, current, open + 1, close, n);
        current.deleteCharAt(current.length() - 1);  // Step 5: undo
    }
    // Step 4: can add ')'?
    if (close < open) {
        current.append(')');
        backtrack(result, current, open, close + 1, n);
        current.deleteCharAt(current.length() - 1);  // Step 5: undo
    }
}
```

### 📊 Complexity

- **Time: O(4ⁿ / √n)** — the nth Catalan number Cₙ counts valid strings; each string takes O(n) to build. Total ≈ O(4ⁿ / √n × n).
- **Space: O(n)** — recursion depth is at most 2n; the `current` StringBuilder is also at most 2n.

---

## 38. Count Salary Categories — LC 1907

**Difficulty:** Medium | **Pattern:** Database (SQL) | **Tags:** Database

### 📌 Problem Statement

Table `Accounts(account_id, income)`. Classify each account: "Low Salary" (< 20000), "Average Salary" (20000–50000 inclusive), "High Salary" (> 50000). Return the count for each category. Return 0 if a category has no accounts.

```
Output:
| category       | accounts_count |
|----------------|----------------|
| Low Salary     | 1              |
| Average Salary | 3              |
| High Salary    | 2              |
```

### 🎤 Interview Framing

The critical requirement: **return 0 for empty categories**, not omit them. A simple GROUP BY CASE won't do that — it only returns categories that appear in the data. The fix: use a UNION of three fixed-category queries, each counting with a WHERE clause.

### ✅ Optimal Solution

**Steps:**

1. Three separate SELECT + COUNT queries, one per salary category.
2. Each query uses WHERE to filter to its range → COUNT(*) returns 0 if no rows match.
3. UNION ALL combines the three fixed-category rows (guarantees all 3 appear).

```sql
-- Step 1–2: one query per category ensures 0 counts appear
SELECT 'Low Salary' AS category,
       COUNT(*) AS accounts_count
FROM Accounts
WHERE income < 20000

UNION ALL

SELECT 'Average Salary' AS category,
       COUNT(*) AS accounts_count
FROM Accounts
WHERE income BETWEEN 20000 AND 50000

UNION ALL

SELECT 'High Salary' AS category,
       COUNT(*) AS accounts_count
FROM Accounts
WHERE income > 50000;
-- Step 3: UNION ALL gives exactly 3 rows always
```

### 📊 Complexity

- **Time: O(n)** — three full scans of the table (or one scan with a CASE expression).
- **Space: O(1)** — result set has exactly 3 rows.

---

## 39. Multiply Strings — LC 43

**Difficulty:** Medium | **Pattern:** Grade-school multiplication simulation | **Tags:** Math, String, Simulation

### 📌 Problem Statement

Given two non-negative integers `num1` and `num2` as strings, return their product as a string. You cannot use big integer libraries or convert directly to integers.

```
"123" × "456" → "56088"
```

### 🎤 Interview Framing

This tests whether you can simulate grade-school multiplication on strings. Key observation: `num1[i] × num2[j]` contributes to result positions `i+j` (tens) and `i+j+1` (ones). Work with an intermediate integer array for positions, then convert to string at the end.

### ❌ Brute Force

Convert to `Long`/`BigInteger`. Multiply. Convert back. Violates the constraint.

### 🧠 From Brute to Optimal

Simulate digit-by-digit: allocate a result array of size `m+n`. For each digit pair `(i, j)`, add the product to `result[i+j+1]`. Then propagate carries from right to left. Finally, skip leading zeros and build the result string.

### ✅ Optimal Solution

**Steps:**

1. Allocate a position array of size `m + n` (max possible digits in product).
2. Multiply each digit pair `(i, j)` — product lands at positions `p1 = i+j` (tens) and `p2 = i+j+1` (ones).
3. Add product to `pos[p2]`, propagate carry to `pos[p1]`.
4. Build result string, skipping leading zeros.

```java
public String multiply(String num1, String num2) {
    int m = num1.length();
    int n = num2.length();
    // Step 1: position array for intermediate results
    int[] pos = new int[m + n];
    // Step 2: multiply every digit pair
    for (int i = m - 1; i >= 0; i--) {
        for (int j = n - 1; j >= 0; j--) {
            int mul = (num1.charAt(i) - '0') * (num2.charAt(j) - '0');
            int p1 = i + j;       // tens position
            int p2 = i + j + 1;   // ones position
            // Step 3: add and propagate carry
            int sum = mul + pos[p2];
            pos[p2] = sum % 10;
            pos[p1] += sum / 10;
        }
    }
    // Step 4: build result, skip leading zeros
    StringBuilder sb = new StringBuilder();
    for (int p : pos) {
        if (!(sb.length() == 0 && p == 0)) {
            sb.append(p);
        }
    }
    return sb.length() == 0 ? "0" : sb.toString();
}
```

### 📊 Complexity

- **Time: O(m × n)** — two nested loops over each digit pair.
- **Space: O(m + n)** — the intermediate position array.

---

## 40. Maximum Subarray — LC 53

**Difficulty:** Medium | **Pattern:** Kadane's Algorithm (DP) | **Tags:** Array, Divide and Conquer, Dynamic Programming

### 📌 Problem Statement

Given an integer array `nums`, find the contiguous subarray with the largest sum and return its sum.

```
Input:  [-2, 1, -3, 4, -1, 2, 1, -5, 4]
Output: 6   // [4, -1, 2, 1]
```

### 🎤 Interview Framing

Kadane's Algorithm is the expected answer. EPAM may ask: *"Can you also return the actual subarray?"* → Track start/end indices when you update the max. Or: *"What if the array can't be empty?"* → Same algorithm (we're always choosing at least one element).

### ❌ Brute Force

Try all subarrays. O(n²) with cumulative sum, O(n³) naively.

### 🧠 From Brute to Optimal

At each position `i`, the maximum subarray ending at `i` is either:
- `nums[i]` alone (start fresh), or
- `nums[i] + maxEndingHere` (extend the previous subarray).

Take whichever is larger. Track the global max across all positions. This is Kadane's — one pass, O(n).

### ✅ Optimal Solution

**Steps:**

1. Init `maxEndingHere` and `maxSoFar` to `nums[0]`.
2. For each element: decide — extend previous subarray or start fresh?
3. `maxEndingHere = max(nums[i], maxEndingHere + nums[i])`.
4. Update global max: `maxSoFar = max(maxSoFar, maxEndingHere)`.

```java
public int maxSubArray(int[] nums) {
    // Step 1: init with first element
    int maxEndingHere = nums[0];
    int maxSoFar = nums[0];
    for (int i = 1; i < nums.length; i++) {
        // Step 2–3: extend or restart?
        maxEndingHere = Math.max(nums[i], maxEndingHere + nums[i]);
        // Step 4: update global best
        maxSoFar = Math.max(maxSoFar, maxEndingHere);
    }
    return maxSoFar;
}
```

### 📊 Complexity

- **Time: O(n)** — single pass; each element processed once.
- **Space: O(1)** — two scalar variables.

---

## 41. Reverse Words in a String — LC 151

**Difficulty:** Medium | **Pattern:** Two Pointers / String | **Tags:** Two Pointers, String

### 📌 Problem Statement

Given a string `s`, reverse the order of the words. Words are separated by spaces. Leading/trailing spaces and multiple spaces between words should be removed. Each word in the output is separated by exactly one space.

```
Input:  "  the sky is blue  "
Output: "the blue is sky"
```

### 🎤 Interview Framing

The clean Java approach: use `s.trim().split("\\s+")` to handle multiple spaces, reverse the array, join with a single space. If asked "no split() allowed" (embedded question), use two-pointer manual scanning. Know both.

### ❌ Brute Force

Split on single space, filter empty strings, reverse, join. Verbose but correct.

### 🧠 From Brute to Optimal

In Java, `split("\\s+")` handles all whitespace runs in one regex. Trim first to avoid a leading empty string in the result.

### ✅ Optimal Solution

**Steps:**

1. Trim and split on whitespace runs (`\\s+`) to handle multiple spaces.
2. Reverse the words array in place with two pointers.
3. Join with a single space.

```java
public String reverseWords(String s) {
    // Step 1: trim + split (handles leading/trailing/multiple spaces)
    String[] words = s.trim().split("\\s+");
    // Step 2: reverse the words array
    int left = 0;
    int right = words.length - 1;
    while (left < right) {
        String temp = words[left];
        words[left] = words[right];
        words[right] = temp;
        left++;
        right--;
    }
    // Step 3: join with single space
    return String.join(" ", words);
}
```

### 📊 Complexity

- **Time: O(n)** — trim + split + reverse + join are all O(n) where n is string length.
- **Space: O(n)** — the words array holds all characters.

---

## 42. Unique Paths — LC 62

**Difficulty:** Medium | **Pattern:** DP / Combinatorics | **Tags:** Math, Dynamic Programming, Combinatorics

### 📌 Problem Statement

An `m × n` grid. A robot starts at the top-left corner and must reach the bottom-right corner. It can only move right or down. Count the number of distinct paths.

```
m = 3, n = 7
Output: 28
```

### 🎤 Interview Framing

Two approaches: DP (fill a grid bottom-up) or combinatorics (the robot makes exactly `m-1` down moves and `n-1` right moves in `m+n-2` total moves → C(m+n-2, m-1)). The combinatorics approach is O(min(m,n)) time and O(1) space — much cleaner. EPAM will appreciate mentioning both.

### ❌ Brute Force

Recursion: from each cell, go right or down. Count all paths to bottom-right.
**Complexity:** Time O(2^(m+n)). Space O(m+n).

### 🧠 From Brute to Optimal — DP

`dp[i][j]` = paths to reach cell (i,j). Transitions: `dp[i][j] = dp[i-1][j] + dp[i][j-1]`. Base case: first row and first column all = 1 (only one way to reach any cell in first row or column).

Space-optimize: since each row only depends on the row above, use a 1D array.

### ✅ Optimal Solution (DP, O(n) space)

**Steps:**

1. Create a 1D dp array of width n, fill with 1 (first row: only one way to reach each cell).
2. For each subsequent row: `dp[j] += dp[j − 1]` (from-above + from-left).
3. `dp[j]` before update = paths from above; `dp[j − 1]` already updated = paths from left.
4. Return `dp[n − 1]`.

```java
public int uniquePaths(int m, int n) {
    // Step 1: first row = all 1s
    int[] dp = new int[n];
    Arrays.fill(dp, 1);
    // Step 2: fill remaining rows
    for (int i = 1; i < m; i++) {
        for (int j = 1; j < n; j++) {
            // Step 3: dp[j] (above) + dp[j-1] (left, already updated)
            dp[j] += dp[j - 1];
        }
    }
    // Step 4: bottom-right cell
    return dp[n - 1];
}
```

### 📊 Complexity

- **Time: O(m × n)** — fill every cell of the (conceptual) grid once.
- **Space: O(n)** — 1D dp array of width n (reduced from O(m×n) by space optimization).

---

## 43. Edit Distance — LC 72

**Difficulty:** Medium | **Pattern:** 2D Dynamic Programming | **Tags:** String, Dynamic Programming

### 📌 Problem Statement

Given two strings `word1` and `word2`, return the minimum number of operations (insert, delete, replace) to transform `word1` into `word2`.

```
word1 = "horse", word2 = "ros"
Output: 3
// horse → rorse (replace h→r)
// rorse → rose  (delete r)
// rose  → ros   (delete e)
```

### 🎤 Interview Framing

This is the classic Levenshtein distance. EPAM uses it to test 2D DP reasoning. The key: `dp[i][j]` = min operations to convert `word1[0..i-1]` to `word2[0..j-1]`. State the recurrence clearly before coding.

### ❌ Brute Force

Recursion: at each position, try all three operations (insert, delete, replace) and take min.
**Complexity:** Time O(3^(m+n)). Exponential.

### 🧠 From Brute to Optimal

`dp[i][j]` depends on three sub-problems:
- Characters match (`word1[i-1] == word2[j-1]`): `dp[i][j] = dp[i-1][j-1]` (no operation needed).
- Replace: `dp[i-1][j-1] + 1`.
- Delete from word1: `dp[i-1][j] + 1`.
- Insert into word1: `dp[i][j-1] + 1`.

Take the minimum of the three edit options.

Base cases: `dp[i][0] = i` (delete all of word1's first i chars), `dp[0][j] = j` (insert all of word2's first j chars).

### ✅ Optimal Solution

**Steps:**

1. Create (m+1) × (n+1) dp table.
2. Base cases: `dp[i][0] = i` (delete all), `dp[0][j] = j` (insert all).
3. If characters match: `dp[i][j] = dp[i−1][j−1]` (no operation).
4. If mismatch: `dp[i][j] = 1 + min(replace, delete, insert)`.
5. Return `dp[m][n]`.

```java
public int minDistance(String word1, String word2) {
    int m = word1.length();
    int n = word2.length();
    // Step 1: dp table
    int[][] dp = new int[m + 1][n + 1];
    // Step 2: base cases
    for (int i = 0; i <= m; i++) {
        dp[i][0] = i;  // delete all of word1[0..i-1]
    }
    for (int j = 0; j <= n; j++) {
        dp[0][j] = j;  // insert all of word2[0..j-1]
    }
    for (int i = 1; i <= m; i++) {
        for (int j = 1; j <= n; j++) {
            // Step 3: characters match → no cost
            if (word1.charAt(i - 1) == word2.charAt(j - 1)) {
                dp[i][j] = dp[i - 1][j - 1];
            } else {
                // Step 4: min(replace, delete, insert) + 1
                dp[i][j] = 1 + Math.min(dp[i - 1][j - 1],
                               Math.min(dp[i - 1][j], dp[i][j - 1]));
            }
        }
    }
    // Step 5: answer
    return dp[m][n];
}
```

### 📊 Complexity

- **Time: O(m × n)** — fill every cell of the (m+1) × (n+1) dp table.
- **Space: O(m × n)** — the dp table. Can be reduced to O(min(m,n)) with a rolling 1D array, but the 2D version is clearest to explain.

---

## 🔗 Cross-References

Problems covered in depth elsewhere in this knowledge base:

| Problem | See |
|---------|-----|
| Two Sum | `DSA/DeepDive/arrays-fundamentals.md` + `DSA/Interview/Playbooks/arrays-and-hashing.md` |
| Group Anagrams | `DSA/Patterns/group-anagrams-problem.md` (full multi-approach dedicated file) |
| LRU Cache | `DSA/DeepDive/hybrid-design-problems.md` |
| 3Sum | `DSA/DeepDive/two-pointers-sliding-window-fundamentals.md` |
| Longest Substring | `DSA/DeepDive/two-pointers-sliding-window-fundamentals.md` |
| Merge Intervals | `DSA/Interview/Playbooks/intervals.md` |
| Coin Change | `DSA/DeepDive/dp-fundamentals.md` |
| House Robber | `DSA/DeepDive/dp-fundamentals.md` |
| Maximum Subarray | `DSA/DeepDive/dp-fundamentals.md` |
| Edit Distance | `DSA/DeepDive/dp-fundamentals.md` |
| Generate Parentheses | `DSA/DeepDive/backtracking-fundamentals.md` |
| Valid Parentheses | `DSA/DeepDive/stacks-queues-fundamentals.md` |
| Move Zeroes | `DSA/DeepDive/arrays-fundamentals.md` |

---

*Last updated: September 2026 — EPAM Top 43 problems (Easy × 13, Medium × 30). SQL problems #11 and #38 use SQL solutions.*

