# Bit Manipulation — Reference

> **Read this file when:** A problem mentions binary representation, XOR, counting set bits, checking powers of 2, or when you're generating all subsets with bitmasks. This is a **toolkit reference**, not a pattern playbook — scan the trick you need, use it.

---

## ⚡ Core Operators — Cheat Sheet

| Operator | Name | Example | Result |
| --- | --- | --- | --- |
| `a & b` | AND — 1 only where both are 1 | `6 & 3` = `110 & 011` | `010` = 2 |
| `a \| b` | OR — 1 where either is 1 | `6 \| 3` = `110 \| 011` | `111` = 7 |
| `a ^ b` | XOR — 1 only where bits differ | `6 ^ 3` = `110 ^ 011` | `101` = 5 |
| `~a` | NOT — flip all bits | `~6` = `~0110` | `-7` (two's complement) |
| `a << k` | Left shift — multiply by 2^k | `3 << 2` | `12` |
| `a >> k` | Signed right shift — divide by 2^k | `-8 >> 1` | `-4` |
| `a >>> k` | Unsigned right shift — fills 0s from left | `-1 >>> 1` | `2147483647` |

> **`>>` vs `>>>`:** `>>` preserves the sign bit (arithmetic shift — negative stays negative). `>>>` always fills with 0s (logical shift — use for reversing bits or when treating int as unsigned, e.g., LC 190).

---

## 🔹 Trick Toolkit

### Family 1 — Lowest Set Bit

```java
// Isolate the lowest set bit (rightmost 1-bit)
// Two's complement: -n flips all bits and adds 1, so n & -n gives only the lowest set bit
int lowest = n & (-n);
// 🔄 Why it works: n = ...abc1000, -n = ...xyz1000 → AND = ...0001000
// Example: n=12 (1100), -n=-12 (two's comp = 0100) → 12 & -12 = 0100 = 4

// Clear the lowest set bit (Kernighan's trick)
// Subtracting 1 flips the lowest set bit and all lower zeros to ones
// ANDing with n clears them all
n = n & (n - 1);
// Example: n=12 (1100), n-1=11 (1011) → 1100 & 1011 = 1000 = 8

// Check if n is a power of 2
// Powers of 2 have exactly one set bit: 1,2,4,8,16,...
// n & (n-1) clears that one bit → result = 0
boolean isPowerOf2 = (n > 0) && (n & (n - 1)) == 0;
// 🔄 Alternative: Integer.bitCount(n) == 1 (but slower due to function call)

// Count all set bits (Brian Kernighan's Algorithm)
// Each n & (n-1) strips one set bit; loop runs exactly [number of set bits] times
int count = 0;
while (n != 0) {
    count++;
    n = n & (n - 1);  // clear the lowest set bit
}
// 🔄 Java built-in: Integer.bitCount(n) — same O(popcount) result, O(1) time
```

---

### Family 2 — XOR Properties

```java
// The three XOR laws to memorize:
// a ^ a = 0        (a number XOR itself = 0)
// a ^ 0 = a        (a number XOR zero = itself)
// XOR is commutative and associative: a ^ b ^ c = c ^ b ^ a

// Find the ONE number that appears an odd number of times
// All pairs cancel (a ^ a = 0). The lone number survives.
int single = 0;
for (int num : nums) {
    single ^= num;  // XOR each number in; pairs cancel, lone number remains
}

// Find missing number in [0, n]
// XOR all indices 0..n with all array values.
// Paired numbers cancel; missing number has no pair.
int missing = n;  // start with n (the expected last number)
for (int i = 0; i < nums.length; i++) {
    missing ^= i ^ nums[i];  // each i XOR'd with both expected and actual value
}

// Swap two integers without temp variable (interview curiosity — never use in real code)
a ^= b;
b ^= a;  // b = b ^ a ^ b = a
a ^= b;  // a = a ^ b ^ a = b
// 🔄 ALWAYS use int temp = a; a = b; b = temp; in real code — clearer and faster
```

---

### Family 3 — Individual Bit Operations

```java
// Check if bit k is set (0-indexed from right)
boolean isSet = ((n >> k) & 1) == 1;
// 🔄 Alternative: (n & (1 << k)) != 0

// Set bit k (turn it to 1)
n = n | (1 << k);

// Clear bit k (turn it to 0)
n = n & ~(1 << k);
// ~(1 << k): all bits 1 except bit k → AND clears only bit k

// Toggle bit k (flip it)
n = n ^ (1 << k);
// XOR with 1 flips: 0→1, 1→0

// Extract all bits k through 0 (mask lower k+1 bits)
int lowerBits = n & ((1 << (k + 1)) - 1);

// Check if two integers have opposite signs (both nonzero)
boolean oppositeSigns = (a ^ b) < 0;
// Negative numbers have 1 as MSB; XOR of two opposite-sign nums has MSB=1 → negative
```

---

### Family 4 — Bitmask for Subsets

```java
// Enumerate ALL subsets of a set of n elements using bitmask
// Bit i in the mask = whether element[i] is included
for (int mask = 0; mask < (1 << n); mask++) {
    // For each subset:
    List<Integer> subset = new ArrayList<>();
    for (int i = 0; i < n; i++) {
        if ((mask & (1 << i)) != 0) {
            // 🔄 Fallback: if (((mask >> i) & 1) == 1)
            subset.add(nums[i]);
        }
    }
    // process subset
}
// Total: 2^n iterations, O(n) inner loop → O(n × 2^n) total
// Only practical for n ≤ 20

// Enumerate all subsets of a given mask (enumerate sub-masks)
// Used in bitmask DP (NOT needed for standard FAANG rounds)
for (int sub = mask; sub > 0; sub = (sub - 1) & mask) {
    // process sub (each is a subset of mask)
}
```

---

### Family 5 — Java Built-ins

```java
// Count set bits (population count / Hamming weight)
int bits = Integer.bitCount(n);         // O(1) — hardware-accelerated
// 🔄 Manual: Brian Kernighan's loop above

// Highest one-bit position (floor of log₂ for positive n)
int highBit = Integer.highestOneBit(n); // returns the value, not the index
int highIdx = 31 - Integer.numberOfLeadingZeros(n);

// Number of leading zeros
int leading = Integer.numberOfLeadingZeros(n);  // 32 for n=0

// Reverse bits
int reversed = Integer.reverse(n);
// 🔄 Manual reversal (LC 190): 32 iterations, shift result left + add rightmost bit of n

// Convert to binary string (for debugging)
String bin = Integer.toBinaryString(n);  // no leading zeros
String binPadded = String.format("%32s", Integer.toBinaryString(n)).replace(' ', '0');
```

---

## 🧭 Recognition Guide — When to Think "Bit Manipulation"

| Problem says... | Reach for... |
| --- | --- |
| "Find the single number" / "one number appears odd times" | XOR all values (Family 2) |
| "Missing number in range [0, n]" | XOR indices with values (Family 2) |
| "Count set bits" / "Hamming weight" | `Integer.bitCount(n)` or Brian Kernighan (Family 1) |
| "Is n a power of 2?" | `n > 0 && (n & (n-1)) == 0` (Family 1) |
| "Generate all subsets" of small n | Bitmask enumeration (Family 4) |
| "XOR of entire array" | XOR all values — properties of XOR cancel pairs |
| "Reverse bits" / "complement" | Individual bit ops or Java built-ins (Families 3, 5) |
| "Two numbers appear exactly once, rest appear twice" | Two-pass XOR split (LC 260) |
| "Count bits in 0..n" | DP with `dp[i] = dp[i >> 1] + (i & 1)` |

---

## 🔬 Key Problems — 10 You Must Know

### LC 136: Single Number

> **Problem:** Every element in `nums` appears twice except one. Find that element. Example: `[2,2,1]` → `1`.
> **Trick:** XOR all elements. Pairs cancel (a^a=0); the lone element survives (a^0=a).

```java
int result = 0;
for (int num : nums) {
    result ^= num;  // pairs cancel; single number remains
}
return result;
```

**Complexity:** O(n) time, O(1) space

---

### LC 191: Number of 1 Bits (Hamming Weight)

> **Problem:** Count the number of 1-bits in the binary representation of a positive integer. Example: `n=11` (binary `1011`) → `3`.
> **Trick 1:** `Integer.bitCount(n)` — one call, done. **Trick 2:** Brian Kernighan — `n & (n-1)` strips one set bit per iteration; loop runs exactly [set bit count] times.

```java
// Brian Kernighan — educational for interview explanation
int count = 0;
while (n != 0) {
    count++;
    n = n & (n - 1);  // strips the lowest set bit each time
}
return count;
// 🔄 One-liner: return Integer.bitCount(n);
```

**Complexity:** O(k) time where k = number of set bits; O(1) space

---

### LC 231: Power of Two

> **Problem:** Return true if n is a power of two. Example: `n=16` → true, `n=6` → false.
> **Trick:** A power of 2 has exactly one set bit. `n & (n-1)` clears the lowest set bit — if result is 0, only one bit was set.

```java
return n > 0 && (n & (n - 1)) == 0;
// n > 0 handles n=0 edge case (0 is not a power of 2)
// 🔄 Verbose: Integer.bitCount(n) == 1 && n > 0
```

**Complexity:** O(1) time, O(1) space

---

### LC 268: Missing Number

> **Problem:** Array contains n distinct numbers in range [0, n]. Find the missing number. Example: `[3,0,1]` → `2`.
> **Trick:** XOR the index i with each `nums[i]`, starting with n. Paired (index, value) cancel; the missing index has no value partner.

```java
int missing = nums.length;  // start with n (the value with no index in the array)
for (int i = 0; i < nums.length; i++) {
    missing ^= i ^ nums[i];  // each index XOR'd with its expected and actual value
}
return missing;
// 🔄 Math approach: n*(n+1)/2 - sum(nums) — watch for overflow with large n
```

**Complexity:** O(n) time, O(1) space

---

### LC 190: Reverse Bits

> **Problem:** Reverse the bits of a 32-bit unsigned integer. Example: `n=43261596` (binary `00000010100101000001111010011100`) → `964176192` (reversed).
> **Trick:** Use `>>>` (unsigned right shift — fills 0, not sign bit). Shift result left each step, add the rightmost bit of n, then shift n right unsigned.

```java
int result = 0;
for (int i = 0; i < 32; i++) {
    // Take rightmost bit of n, OR it into result
    result = (result << 1) | (n & 1);
    // Unsigned right shift: ensures 0 fills the MSB (n is treated as unsigned)
    n >>>= 1;
}
return result;
// 🔄 One-liner: return Integer.reverse(n);
```

**Complexity:** O(1) time (always 32 iterations), O(1) space

---

### LC 260: Single Number III

> **Problem:** Two numbers appear exactly once; all others appear exactly twice. Find those two unique numbers. Example: `[1,2,1,3,2,5]` → `[3,5]`.
> **Trick:** XOR all → get `a ^ b`. Find any differing bit (use `diff & (-diff)` to isolate lowest). Split array by that bit — each group has one unique number; XOR within each group.

```java
int xor = 0;
for (int num : nums) xor ^= num;  // xor = a ^ b (a,b are the two unique numbers)
// Isolate a bit where a and b differ (any set bit in xor works)
int diff = xor & (-xor);  // lowest set bit of xor
int a = 0;
for (int num : nums) {
    // Partition: numbers with diff-bit set go to group a, others to group b
    if ((num & diff) != 0) {
        a ^= num;  // pairs cancel; unique number in this partition survives
    }
}
return new int[]{a, xor ^ a};  // xor ^ a = b (since a ^ b = xor)
```

**Complexity:** O(n) time, O(1) space

---

### LC 338: Counting Bits

> **Problem:** Return an array `result` where `result[i]` = number of 1-bits in i, for `i` from 0 to n. Example: `n=5` → `[0,1,1,2,1,2]`.
> **Trick:** DP relationship: `dp[i] = dp[i >> 1] + (i & 1)`. Shifting right one bit = same number but one bit shorter; add 1 if the removed bit was a 1.

```java
int[] dp = new int[n + 1];
// dp[0] = 0 by default
for (int i = 1; i <= n; i++) {
    // i >> 1: drop the rightmost bit (already counted in dp[i>>1])
    // (i & 1): 1 if the dropped bit was set, 0 otherwise
    dp[i] = dp[i >> 1] + (i & 1);
}
return dp;
```

**Complexity:** O(n) time, O(n) space

---

### LC 78: Subsets (Bitmask approach)

> **Problem:** Return all subsets (the power set) of an array of unique integers. Example: `nums=[1,2,3]` → `[[],[1],[2],[1,2],[3],[1,3],[2,3],[1,2,3]]`.
> **Trick:** With n elements, there are 2^n subsets. Use an integer mask from 0 to 2^n - 1 where bit i = "include nums[i]".

```java
int n = nums.length;
List<List<Integer>> result = new ArrayList<>();
for (int mask = 0; mask < (1 << n); mask++) {
    List<Integer> subset = new ArrayList<>();
    for (int i = 0; i < n; i++) {
        if ((mask & (1 << i)) != 0) {
            // Bit i is set in mask → include nums[i] in this subset
            subset.add(nums[i]);
        }
    }
    result.add(subset);
}
return result;
// 🔄 Alternative: backtracking (see backtracking.md Pattern 1)
```

**Complexity:** O(n × 2^n) time, O(n × 2^n) space

---

### LC 201: Bitwise AND of Numbers Range

> **Problem:** Return the bitwise AND of all numbers in range `[left, right]`. Example: `left=5 (101), right=7 (111)` → `4 (100)`.
> **Trick:** The AND of a range keeps only the common prefix of all numbers in the range. Shift both left and right right until they're equal — that common prefix is the answer (shift count tells you how far to shift back).

```java
int shift = 0;
while (left != right) {
    // Keep stripping the rightmost bit until left and right converge
    left >>= 1;
    right >>= 1;
    shift++;
}
// Shift the common prefix back into position
return left << shift;
```

**Complexity:** O(log n) time, O(1) space

---

### LC 137: Single Number II

> **Problem:** Every element in `nums` appears three times except one. Find the element that appears once. Example: `[2,2,3,2]` → `3`.
> **Trick:** Count each bit position across all numbers. If the count is divisible by 3, the unique number's bit is 0; otherwise it's 1.

```java
int result = 0;
for (int i = 0; i < 32; i++) {
    int bitSum = 0;
    for (int num : nums) {
        // Count how many numbers have bit i set
        bitSum += (num >> i) & 1;
    }
    // If count % 3 != 0, the single number has bit i set
    if (bitSum % 3 != 0) {
        result |= (1 << i);
    }
}
return result;
// 🔄 Advanced: two-variable state machine (ones, twos) — O(n) time same space, harder to explain
```

**Complexity:** O(32n) = O(n) time, O(1) space

---

## 🧩 Speed Drill — 5 Minutes

**Part 1 — Trick Recall (2 minutes)**

For each description, write the one-line Java expression:

1. Check if `n` is a power of 2 → ___
2. Count set bits in `n` (Java built-in) → ___
3. Clear the lowest set bit of `n` → ___
4. Isolate the lowest set bit of `n` → ___
5. XOR all elements in `nums[]` to find the single number → ___

**Answers:**
1. `n > 0 && (n & (n-1)) == 0`
2. `Integer.bitCount(n)`
3. `n & (n-1)`
4. `n & (-n)`
5. `int r = 0; for (int x : nums) r ^= x; return r;`

**Part 2 — Which Trick? (2 minutes)**

1. "Find missing number in [0..n]" → ___
2. "Two numbers appear once, rest appear twice" → ___
3. "Generate all 2^n subsets" → ___
4. "Count bits in every number 0..n efficiently" → ___

**Answers:** 1. XOR indices and values, 2. XOR all → split by differing bit, 3. Bitmask 0 to 2^n-1, 4. DP with `dp[i] = dp[i>>1] + (i&1)`

**Part 3 — Write It (1 minute)**

From memory, write Brian Kernighan's algorithm for counting set bits (3 lines: initialize, loop, return).

**Scoring:** All Part 1 correct + Part 3 written without peeking → ready. Missed `n > 0` check in power-of-2 → re-read; n=0 is the subtle edge case.

---

## ⚠️ Common Gotchas

- **`n & (n-1)` fails for n=0** — check `n > 0` before using it for power-of-2 detection. `0 & -1 = 0` looks like a power of 2 incorrectly.
- **`>>` vs `>>>`** — use `>>>` when you want to treat an int as unsigned (reverse bits, sliding window over unsigned value). `>>` propagates the sign bit — wrong for negative numbers.
- **`1 << k` overflow for k ≥ 31** — `1 << 31` is `Integer.MIN_VALUE` (valid). `1 << 32` wraps around to 1 (wrong!). Use `1L << k` when k can be ≥ 31.
- **Operator precedence** — bitwise operators have lower precedence than `==`. Always parenthesize: `(n & 1) == 1`, not `n & 1 == 1` (which is `n & (1 == 1)` = `n & true` → compile error).
- **Bitmask subsets only for n ≤ 20** — `1 << 30` = 1 billion iterations. Only use bitmask enumeration when n is small (≤ 20, typically ≤ 15 for competitive programming).

---

## 🔗 Cross-References

- **Subsets:** `../Interview/Playbooks/backtracking.md` — Pattern 1 shows the backtracking approach to subsets; bitmask approach here is an alternative for small n
- **Arrays:** `../Interview/Playbooks/arrays-and-hashing.md` — XOR-based missing number is related to frequency counting patterns
- **DP (bitmask DP):** skipped intentionally — too rare for standard FAANG rounds. Research independently if needed for Google hard.
- **Java operators quick reference:** `../Reference/code-style-for-dsa-reference.md`

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| June 2026 | **File created.** Bit Manipulation Reference — 5 trick families (lowest-set-bit, XOR, individual bits, bitmask subsets, Java built-ins), recognition guide, 10 canonical problems with key insight + code, speed drill. Placed in Reference/ (toolkit format) not Interview/Playbooks/ (pattern format). |
