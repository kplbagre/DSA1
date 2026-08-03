# Confluent DSA — Concepts Before Problems

> **Format:** Follow `_format.md` in this folder.
>
> **Purpose:** Don't jump into problems cold. Read this first — it covers the prerequisite concepts you must have cold before touching the Confluent problem set. Each section links to your existing deep-dive notes for full coverage.

---

## 1. HashMap Internals — How It Actually Works

**Why you need this:** Confluent asks "Design a HashMap" (LC 706), "Build a KV Store with TTL," and "Build an Inverted Index." All require knowing what happens UNDER the hood, not just `map.put()`.

**The 60-second version:**

```
  key.hashCode()  →  hash % tableSize  →  bucket index
                                               │
                                    ┌──────────▼──────────┐
                                    │      bucket[]       │
                                    │  [0]: null          │
                                    │  [1]: (Alice,25)    │
                                    │  [2]: (Charlie,35)  │
                                    │  [3]: (Bob,30) → (David,28)  ← collision chain
                                    └─────────────────────┘

KEY INVARIANT:
   O(1) average because hash spreads keys evenly across buckets.
   O(n) worst case when all keys collide into one bucket (bad hash function).
```

| Concept | What to know | Interview phrasing |
|---|---|---|
| **Hash function** | `hashCode() % capacity` maps key to bucket index | "How do you convert a key to an array index?" |
| **Collision** | Two keys map to same bucket → chain them (linked list) or probe (open addressing) | "What happens when two keys hash to the same index?" |
| **Load factor** | `size / capacity`. Java default threshold: 0.75 | "When do you resize?" |
| **Rehashing** | Double capacity, re-insert all entries with new `% capacity` | "What's the cost of resize?" → O(n) per resize, amortized O(1) per insert |
| **Java 8 treeification** | When a single bucket chain exceeds 8 entries, Java converts it from linked list to red-black tree → O(log n) worst case instead of O(n) | "What if all keys collide?" |

**Full coverage:** [`DSA/DeepDive/hashmaps-fundamentals.md`](../../../DSA/DeepDive/hashmaps-fundamentals.md) — §What Is a HashMap, §Terminology, §Visual (collision & rehashing)

---

## 2. DLL + HashMap Combo — The LRU Cache Building Block

**Why you need this:** Thread-safe LRU Cache with TTL is Tier 1 at Confluent. The core data structure is always the same — HashMap for O(1) lookup + Doubly Linked List for O(1) insert/remove/reorder.

**The structure:**

```
  HashMap<Key, DLLNode>          Doubly Linked List (most recent → least recent)

  "A" → node_A                  HEAD ↔ node_C ↔ node_A ↔ node_B ↔ TAIL
  "B" → node_B                  (most recent)              (least recent = evict)
  "C" → node_C

  get("A"):
    1. HashMap lookup → O(1) → find node_A
    2. Unlink node_A from current position → O(1) (DLL pointer surgery)
    3. Move node_A to head (most recently used) → O(1)

  put(new_key) when full:
    1. Remove node before TAIL (least recently used) → O(1)
    2. Remove that key from HashMap → O(1)
    3. Insert new node at HEAD → O(1)
    4. Add new key to HashMap → O(1)

KEY INVARIANT:
   Every operation is O(1) because HashMap gives direct pointer to DLL node,
   and DLL insert/remove is O(1) with direct pointer access.
   Without HashMap: finding a node in DLL is O(n) scan.
   Without DLL: reordering in HashMap requires shifting entries.
```

**Full coverage:** [`LLD/Problems/lru-cache/lru-cache.md`](../../../LLD/Problems/lru-cache/lru-cache.md) — full implementation with DLL node class

---

## 3. Backtracking Template — The Decision Tree Framework

**Why you need this:** Sudoku (LC 36/37) and Wildcard/Regex Matching (LC 44/10) are Tier 1 at Confluent. Both are backtracking problems. The template is identical every time — only the "choice space" changes.

**The mental model:**

```
  Backtracking = DFS on an implicit decision tree

                        start
                       / | \
                    choice choices choices
                   /  |    |  \     |
                ...  ...  ...  ... ...
                (leaf = valid solution or dead end)

  At each node:
    1. CHOOSE — pick one option from available choices
    2. EXPLORE — recurse with that choice applied
    3. UNCHOOSE — undo the choice (backtrack) before trying the next option
```

**The universal template:**

**Steps in plain English:**

1. **Base case** — if we've filled all positions, record the solution.
2. **Iterate choices** — for each valid option at the current position.
3. **Make the choice** — apply it (place number, mark cell, append character).
4. **Recurse** — move to the next position.
5. **Undo the choice** — restore state so sibling branches see a clean slate.

```java
public void backtrack(State state, int position, List<Result> results) {
    // Step 1 — base case: all positions filled
    if (position == totalPositions) {
        results.add(new Result(state));
        return;
    }

    // Step 2 — iterate available choices at this position
    for (Choice choice : getChoices(state, position)) {
        // Step 3 — make the choice
        applyChoice(state, choice);

        // Step 4 — recurse to next position
        backtrack(state, position + 1, results);

        // Step 5 — undo the choice (backtrack)
        undoChoice(state, choice);
    }
}
```

**Confluent-specific application:**
- **Sudoku:** position = each empty cell; choices = digits 1-9 that don't violate row/col/box constraints
- **Regex matching:** position = index in pattern + index in string; choices = match literal, match `.`, expand `*`

**Full coverage:** [`DSA/DeepDive/backtracking-fundamentals.md`](../../../DSA/DeepDive/backtracking-fundamentals.md) — full template, N-Queens, permutations, combinations

---

## 4. Prefix Sum Technique — Subarray Sum in O(1)

**Why you need this:** LC 1664 (Ways to Make Fair Array) uses prefix sums. It's a medium-frequency Confluent question.

**The idea:**

```
  Array:       [2, 1, 6, 4]
  Prefix sum:  [0, 2, 3, 9, 13]
                ↑              ↑
              prefix[0]=0    prefix[4]=sum of all

  Sum of subarray [i..j] = prefix[j+1] - prefix[i]
  Example: sum of [1..2] = prefix[3] - prefix[1] = 9 - 2 = 7  (1 + 6 = 7 ✓)
```

**When to reach for it:**
- "Sum of subarray" → prefix sum
- "Count subarrays with sum = K" → prefix sum + HashMap (Pattern 4 in hashmaps)
- "Remove one element and check balance" → prefix sums from left AND right

**Full coverage:** [`DSA/DeepDive/hashmaps-fundamentals.md`](../../../DSA/DeepDive/hashmaps-fundamentals.md) — Pattern 4 (Prefix Sum + HashMap), [`DSA/DeepDive/arrays-fundamentals.md`](../../../DSA/DeepDive/arrays-fundamentals.md)

---

## 5. Two-Pointer / Greedy on Strings

**Why you need this:** LC 1768 (Merge Strings Alternately) and the function signature matcher use two-pointer traversal over strings.

**The pattern:**

```java
int i = 0;
int j = 0;
while (i < s1.length() && j < s2.length()) {
    // process s1[i] and s2[j]
    // advance one or both pointers based on condition
    i++;
    j++;
}
// handle remaining characters in whichever string is longer
```

**Full coverage:** [`DSA/DeepDive/two-pointers-sliding-window-fundamentals.md`](../../../DSA/DeepDive/two-pointers-sliding-window-fundamentals.md)

---

## 6. Stack-Based Parsing — Nested Structure Problems

**Why you need this:** LC 726 (Number of Atoms) requires parsing nested parenthesized expressions like `K4(ON(SO3)2)2`. Stack is the go-to for any nested/recursive structure parsing.

> **Coverage note:** [`DSA/DeepDive/stacks-queues-fundamentals.md`](../../../DSA/DeepDive/stacks-queues-fundamentals.md) mentions LC 726 as a one-line "transfer" from bracket matching, but does NOT walk through the "push map, pop and multiply" pattern. **This section is the deep reference for that pattern.**

**When to reach for stack-based parsing:**
- Nested parentheses / brackets
- Mathematical expression evaluation
- HTML/XML tag matching
- Any problem with "scopes" that nest

### The "Push Map, Pop and Multiply" Pattern

The key idea: every `(` creates a new **scope** (a fresh frequency map). Every `)` closes the scope — multiply all counts in the current scope by the number after `)`, then merge into the parent scope.

### 🎨 Visual — Step-by-Step Trace for `K4(ON(SO3)2)2`

```
  Input: K 4 ( O N ( S O 3 ) 2 ) 2
                                          Stack (bottom → top)        Current Map
  ──────────────────────────────────────────────────────────────────────────────────
  'K'  → element = "K"                    []                          {}
  '4'  → count = 4, store K:4            []                          {K:4}
  '('  → PUSH current map onto stack      [{K:4}]                     {}  ← fresh scope
  'O'  → element = "O", count = 1         [{K:4}]                     {O:1}
  'N'  → element = "N", count = 1         [{K:4}]                     {O:1, N:1}
  '('  → PUSH current map onto stack      [{K:4}, {O:1, N:1}]        {}  ← fresh scope
  'S'  → element = "S", count = 1         [{K:4}, {O:1, N:1}]        {S:1}
  'O'  → element = "O", count = 1         [{K:4}, {O:1, N:1}]        {S:1, O:1}
  '3'  → update O count to 3              [{K:4}, {O:1, N:1}]        {S:1, O:3}
  ')'  → read multiplier = 2              
         multiply all: {S:2, O:6}         
         POP parent: {O:1, N:1}
         MERGE: {O:1+6=7, N:1, S:2}       [{K:4}]                     {O:7, N:1, S:2}
  ')'  → read multiplier = 2
         multiply all: {O:14, N:2, S:4}
         POP parent: {K:4}
         MERGE: {K:4, O:14, N:2, S:4}     []                          {K:4, O:14, N:2, S:4}

  Result: K4N2O14S4 (sorted alphabetically)
```

### Template Code

**Steps in plain English:**

1. **Initialize** — empty stack and empty current-scope map.
2. **Scan characters** — classify each as uppercase letter, lowercase letter, digit, `(`, or `)`.
3. **On `(`** — push current map onto stack, start fresh empty map.
4. **On `)`** — read the multiplier after `)`, multiply every count in current map, pop the parent map, merge current into parent.
5. **On element** — parse full element name (uppercase + optional lowercase), parse count (default 1), add to current map.

```java
public Map<String, Integer> parseFormula(String formula) {
    Deque<Map<String, Integer>> stack = new ArrayDeque<>();
    Map<String, Integer> current = new TreeMap<>();
    int i = 0;
    int n = formula.length();

    while (i < n) {
        char c = formula.charAt(i);

        if (c == '(') {
            // Step 3 — push current scope, start fresh
            stack.push(current);
            current = new TreeMap<>();
            i++;
        } else if (c == ')') {
            i++;
            // Read multiplier after ')'
            int multiplier = 0;
            while (i < n && Character.isDigit(formula.charAt(i))) {
                multiplier = multiplier * 10 + (formula.charAt(i) - '0');
                i++;
            }
            if (multiplier == 0) {
                multiplier = 1;
            }
            // Multiply all counts in current scope
            for (Map.Entry<String, Integer> entry : current.entrySet()) {
                entry.setValue(entry.getValue() * multiplier);
            }
            // Pop parent and merge
            Map<String, Integer> parent = stack.pop();
            for (Map.Entry<String, Integer> entry : current.entrySet()) {
                parent.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
            current = parent;
        } else {
            // Step 5 — parse element name: uppercase + optional lowercase letters
            StringBuilder element = new StringBuilder();
            element.append(c);
            i++;
            while (i < n && Character.isLowerCase(formula.charAt(i))) {
                element.append(formula.charAt(i));
                i++;
            }
            // Parse count (default 1)
            int count = 0;
            while (i < n && Character.isDigit(formula.charAt(i))) {
                count = count * 10 + (formula.charAt(i) - '0');
                i++;
            }
            if (count == 0) {
                count = 1;
            }
            current.merge(element.toString(), count, Integer::sum);
        }
    }
    return current;
}
```

- **Time:** O(n) where n = formula length — each character is processed exactly once. The merge after `)` is O(k) where k = unique elements in the inner scope, but total merge work across all `)` is bounded by O(n) (each element is merged at most once per nesting level).
- **Space:** O(n) — stack depth = nesting depth; each scope map stores elements. Total across all scope maps ≤ n characters worth of element names.

### Why This Pattern Transfers

The "push scope, pop and merge" structure is identical for:
- **LC 726** (Number of Atoms): scope = element frequency map, merge = add counts
- **LC 394** (Decode String): scope = partially built string, merge = concatenate
- **LC 385** (Mini Parser): scope = NestedInteger, merge = add child
- **Calculator problems** (LC 224, 227, 772): scope = partial expression result, merge = apply operator

The only thing that changes between these problems is **what lives in the scope map and how you merge**.

---

## 7. File I/O in Java — Reading Huge Files

**Why you need this:** The "tail command" problem (read last N lines of a huge file that doesn't fit in memory) is Tier 2 at Confluent. This is NOT covered in any existing DSA note — it's a systems-flavored coding problem.

**Key classes:**

```java
// RandomAccessFile — lets you seek to any byte position in a file
// Unlike BufferedReader which reads sequentially from start
RandomAccessFile raf = new RandomAccessFile("huge.log", "r");

// seek() jumps to a byte offset — O(1), no reading needed
raf.seek(raf.length() - 1);  // jump to last byte

// read() reads one byte at current position
int b = raf.read();

// readLine() reads from current position to next newline
String line = raf.readLine();
```

**Tail command approach:**

**Steps in plain English:**

1. **Open file with `RandomAccessFile`** — gives random access by byte offset.
2. **Seek to end** — `raf.seek(raf.length() - 1)`.
3. **Scan backwards byte-by-byte** — count newline characters (`\n`).
4. **When you've found N newlines** — you've located the start of the last N lines.
5. **Seek to that position and read forward** — `readLine()` in a loop.

```java
public List<String> tail(String filePath, int n) throws IOException {
    RandomAccessFile raf = new RandomAccessFile(filePath, "r");
    long fileLength = raf.length();
    int newlineCount = 0;
    long pos = fileLength - 1;

    // Step 1 — scan backwards counting newlines
    while (pos > 0 && newlineCount <= n) {
        raf.seek(pos);
        if (raf.readByte() == '\n') {
            newlineCount++;
        }
        pos--;
    }

    // Step 2 — position is now at start of last N lines
    // (pos+2 because we decremented one extra and skipped the newline itself)
    if (pos > 0) {
        raf.seek(pos + 2);
    } else {
        raf.seek(0);
    }

    // Step 3 — read forward line by line
    List<String> lines = new ArrayList<>();
    String line;
    while ((line = raf.readLine()) != null) {
        lines.add(line);
    }
    raf.close();
    return lines;
}
```

- **Time:** O(B) where B = number of bytes in the last N lines (NOT the entire file)
- **Space:** O(N × L) where L = average line length — only stores the result, not the whole file

**No existing coverage in this repo.** This section IS the deep reference.

### Java I/O Classes — When to Use Which

| Class | Access Pattern | Buffered? | Use Case |
|---|---|---|---|
| `FileReader` + `BufferedReader` | Sequential, forward-only | Yes (8KB default) | Read line-by-line from start; most common for normal files |
| `RandomAccessFile` | Random access, any direction | No (but you can wrap it) | Jump to any byte offset; required for "tail" and "read from middle" problems |
| `FileInputStream` | Sequential, byte-level | No (wrap with `BufferedInputStream`) | Binary files; when you need raw bytes, not decoded characters |
| `Files.readAllLines(Path)` | Read entire file at once | N/A | Small files only; loads everything into memory |

### The Crucial Difference for Interview Problems

```
  BufferedReader                            RandomAccessFile
  ─────────────                             ──────────────────
  Start ──▶ read ──▶ read ──▶ read ──▶ End   Start ─────────────── End
  (can only go forward)                          ▲     ▲     ▲
                                              seek() seek() seek()
                                            (jump anywhere in O(1))

  "Read last 10 lines of a 100GB file"
    BufferedReader: must read all 100GB to find the last 10 lines  ← O(F) entire file
    RandomAccessFile: seek to end, scan backwards for 10 newlines  ← O(N×L) last 10 lines only
```

### Edge Cases to Handle

| Edge Case | What happens | Fix |
|---|---|---|
| File ends with `\n` | Counting from last byte would count a "phantom" empty line | Start from `fileLength - 2`, not `fileLength - 1` |
| File has fewer than N lines | `pos` reaches 0 before finding N newlines | Check `pos > 0` in loop; if exhausted, seek to 0 and return all lines |
| Empty file | `fileLength == 0` | Return empty list immediately |
| Multi-byte characters (UTF-8) | `readByte()` reads raw bytes; a multi-byte char's middle byte might equal `\n` (0x0A) | In practice, `\n` (0x0A) never appears as a continuation byte in UTF-8 — safe for newline counting. But `readLine()` doesn't handle UTF-8. For full UTF-8 support, use `new BufferedReader(new InputStreamReader(new FileInputStream(raf.getFD()), StandardCharsets.UTF_8))` after seeking. |

---

## 8. Dynamic Programming on Two Strings — Matching Problems

**Why you need this:** Wildcard Matching (LC 44) and Regex Matching (LC 10) are both 2D DP on two strings. The template is the same family as Edit Distance (LC 72) and LCS.

> **Coverage note:** [`DSA/DeepDive/dp-fundamentals.md`](../../../DSA/DeepDive/dp-fundamentals.md) lists LC 44 as 🔴 Senior+ "do not cold-solve." The LCS family (Family 5) covers Edit Distance but NOT wildcard/regex matching transitions. **This section is the deep reference for the matching DP pattern.**

### The Core Mental Model — Why It's a 2D Table

You have two strings: `s` (text, length m) and `p` (pattern, length n). The question "does s match p?" has optimal substructure: if the last characters match, the answer depends on "does s[0..m-2] match p[0..n-2]?" This recursive dependency on two prefixes gives a 2D table.

**State:** `dp[i][j]` = "does `s[0..i-1]` match `p[0..j-1]`?"

**Base cases:**
- `dp[0][0] = true` — empty string matches empty pattern
- `dp[i][0] = false` for i > 0 — non-empty string can't match empty pattern
- `dp[0][j]` — depends on pattern: only `*` can match empty string

### The Transition Rules — This Is What Changes Between Problems

**Wildcard Matching (LC 44) — `?` matches one char, `*` matches any sequence:**

```
  if p[j-1] == s[i-1] or p[j-1] == '?':
      dp[i][j] = dp[i-1][j-1]          ← characters match, consume both

  if p[j-1] == '*':
      dp[i][j] = dp[i][j-1]            ← * matches EMPTY sequence (skip *)
                 || dp[i-1][j]          ← * matches s[i-1] and continues matching more
```

**Regex Matching (LC 10) — `.` matches one char, `*` means "zero or more of preceding":**

```
  if p[j-1] == s[i-1] or p[j-1] == '.':
      dp[i][j] = dp[i-1][j-1]          ← characters match, consume both

  if p[j-1] == '*':
      dp[i][j] = dp[i][j-2]            ← zero occurrences of preceding char (skip "x*")
                 || (p[j-2] matches s[i-1]) && dp[i-1][j]
                                         ← one+ occurrences: preceding char matches, consume s[i-1]
```

**The critical difference:** In LC 44, `*` is standalone (matches any sequence). In LC 10, `*` modifies the preceding character (`a*` means "zero or more a's"). This changes the transition AND the base case.

### 🎨 Visual — DP Table for Wildcard: s="adcb", p="a*b"

```
         ""    a    *    b
    ┌─────┬─────┬─────┬─────┐
  ""|  T  |  F  |  F  |  F  |   dp[0][0]=T (empty matches empty)
    ├─────┼─────┼─────┼─────┤   dp[0][2]=F (a* can't match empty in wildcard)
  a |  F  |  T  |  T  |  F  |   dp[1][1]=T (a==a, look at dp[0][0]=T)
    ├─────┼─────┼─────┼─────┤   dp[1][2]=T (* matches empty: dp[1][1]=T)
  d |  F  |  F  |  T  |  F  |   dp[2][2]=T (* eats 'd': dp[1][2]=T)
    ├─────┼─────┼─────┼─────┤
  c |  F  |  F  |  T  |  F  |   dp[3][2]=T (* eats 'c': dp[2][2]=T)
    ├─────┼─────┼─────┼─────┤
  b |  F  |  F  |  T  |  T  |   dp[4][3]=T (b==b, look at dp[3][2]=T) ✓
    └─────┴─────┴─────┴─────┘

  How to read: dp[4][3] = "does 'adcb' match 'a*b'?" = TRUE

  Each * cell: look LEFT (dp[i][j-1], * = empty) OR UP (dp[i-1][j], * eats char)
  Each literal cell: look DIAGONAL (dp[i-1][j-1], both consumed)
```

### Why the `*` Transition Works

Think of `*` as having two choices at every position:
1. **Stop matching** (match empty sequence from here) → look at `dp[i][j-1]` — same row, skip the `*`
2. **Keep matching** (consume `s[i-1]` and stay at `*`) → look at `dp[i-1][j]` — one fewer char in s, `*` still active

This is why it's `OR` — if EITHER choice leads to a match, the result is true.

### Space Optimization

The full table is O(m×n). But each row depends only on the previous row → can optimize to O(n) with a rolling array:

```java
// Instead of dp[m+1][n+1], use two rows: prev[] and curr[]
boolean[] prev = new boolean[n + 1];
boolean[] curr = new boolean[n + 1];
// Fill prev (row 0 = base case), then for each row, fill curr from prev
// After filling, swap: prev = curr
```

### The Template That Covers All 2D String DP

| Problem | `dp[i][j]` means | Diagonal | Up | Left |
|---|---|---|---|---|
| **Wildcard** (LC 44) | s[0..i-1] matches p[0..j-1] | chars match | `*` eats char | `*` = empty |
| **Regex** (LC 10) | s[0..i-1] matches p[0..j-1] | chars match | `x*` eats char | — (use j-2 for zero of `x*`) |
| **Edit Distance** (LC 72) | min edits for s[0..i-1] → t[0..j-1] | chars match (0 cost) | delete from s | insert into s |
| **LCS** (LC 1143) | longest common subsequence length | chars match (+1) | skip s[i] | skip t[j] |

Same skeleton, different cell logic.

---

## 9. Concurrency — From Zero to Interview-Ready

**Why you need this:** Confluent has a dedicated concurrency round. Every "design a component" problem gets a "now make it thread-safe" follow-up. This section makes you self-contained — you should be able to code the solution AND answer every cross-question listed in `04-concurrency-problems.md` without leaving this file.

**How to read this section:** Go through 9a → 9i in order on your first read. Once you've done that, the quick-pick table at the end (§9j) will make sense as a 10-second revision cheat sheet — every row will click because you've already seen the concept.

> **Want to understand the *why* at the hardware/JVM level?** The code templates below are self-contained, but if you want to understand *why* `volatile` works, what "happens-before" actually means, or why CAS beats `synchronized` at low contention — read [`concurrency-fundamentals.md`](./concurrency-fundamentals.md) first. That file is the conceptual deep dive; this section is the code-and-cross-question cheat sheet.

### 9a. The Two Root Problems — Why Concurrency is Hard

Every concurrency bug is caused by one or both of these:

**Root Problem 1 — Atomicity:** An operation that looks like one step is actually many, and another thread can run between them.

**Root Problem 2 — Visibility:** A value written by one thread may sit in that CPU's cache and never reach main memory — so other CPUs read a stale value.

### 🎨 Visual — The Classic Race Condition (counter++)

```
  Shared: count = 0

  Thread A                          Thread B
  ─────────────────                 ─────────────────
  1. read count → 0
                                    2. read count → 0  ← stale (cache)
  3. increment → 1
  4. write count = 1
                                    5. increment → 1
                                    6. write count = 1  ← overwrites A's write!

  Expected: count = 2   Actual: count = 1

  This is read-modify-write: three steps (read, modify, write) that look like one.
  Another thread can insert itself between any two steps.
```

**What fixes atomicity:** `synchronized` or `AtomicInteger` — they make read-modify-write one indivisible CPU operation.

**What fixes visibility:** `volatile`, `synchronized` (both flush CPU caches on entry/exit), or `Atomic*` classes (all reads/writes go to main memory).

---

### 9b. `synchronized` — The Foundation

**What it gives you:** mutual exclusion (only one thread inside the block) + visibility (all writes before unlock are visible to the next thread that acquires the lock). Every Java object has a built-in **monitor** (the lock embedded in the object itself — like a single-key padlock on the object's door) that `synchronized` uses.

**Reentrant:** if the same thread already holds the lock, it can re-enter the same synchronized block without deadlocking. This matters when `synchronized` methods call each other.

```java
public class Counter {

    private int count = 0;

    // Synchronized method — lock on 'this'
    public synchronized void increment() {
        count++;
    }

    // Synchronized block — lock on a specific object (finer grained)
    public void add(int n) {
        synchronized (this) {
            count += n;
        }
    }
}
```

**Cross-Q: "Does `synchronized` guarantee visibility?"**
Yes — the Java Memory Model (JMM) guarantees that all writes made inside a `synchronized` block are visible to any subsequent thread that acquires the same monitor.

**Cross-Q: "`synchronized` vs `ReentrantLock`?"**
Use `synchronized` unless you need one of: `tryLock(timeout)`, `lockInterruptibly()`, or fair mode. `synchronized` is simpler and the JVM optimizes it aggressively.

---

### 9c. `volatile` — Visibility Without Locking

`volatile` guarantees every read goes to main memory (not CPU cache) and every write goes directly to main memory. It is NOT a lock — it gives zero atomicity guarantee.

```java
// SAFE: one writer, many readers
private volatile boolean running = true;

// Thread reads the flag each iteration — no stale cache value
public void workerLoop() {
    while (running) {
        // do work
    }
}

// Main thread sets it to stop workers
public void stop() {
    running = false;
}
```

**Cross-Q: "Why can't `volatile` replace `synchronized`?"**
`volatile` prevents stale reads, but `i++` is still three steps (read, increment, write). Between the read and the write, another thread can also read the old value. `volatile` doesn't make those three steps atomic.

**The line:** `volatile` = one writer updating a flag; `synchronized` = any read-modify-write.

---

### 9d. `AtomicInteger` and `AtomicReference` — CAS Without Locks

**CAS (Compare-And-Swap):** a single CPU instruction that reads a field, compares it to an expected value, and only writes the new value if the comparison succeeds. If the field changed since the read, the swap fails — caller retries. No lock, no OS scheduling.

**`AtomicInteger`:** counter operations without `synchronized`.

```java
AtomicInteger count = new AtomicInteger(0);

// All three are single atomic CPU operations:
count.incrementAndGet();          // count++, returns new value
count.decrementAndGet();          // count--, returns new value
count.getAndAdd(5);               // count += 5, returns old value
count.compareAndSet(0, 1);        // if count == 0, set to 1. Returns true if swapped.
```

**`AtomicReference` — state machine pattern:**

```java
// Job status: only one thread can win the PENDING → RUNNING transition
private final AtomicReference<Status> status = new AtomicReference<>(Status.PENDING);

// Returns true only for the one thread that wins the CAS
public boolean tryStart() {
    return status.compareAndSet(Status.PENDING, Status.RUNNING);
}

public boolean tryCancel() {
    return status.compareAndSet(Status.PENDING, Status.CANCELLED);
}
```

**Cross-Q: "Why is `AtomicReference` + CAS faster than `synchronized`?"**
`synchronized` acquires an OS-level mutex — when contended, the thread is suspended and context-switched (microseconds of overhead). CAS is a single CPU instruction — it never blocks. Under **low contention**, CAS wins by 5-10x. Under **high contention**, CAS loops (spin) and may burn more CPU than a sleeping thread would.

**Cross-Q: "When is `AtomicReference` not enough?"**
When you need to update two fields together atomically (e.g., both `runningSum` and `activeCount` in the KV Store average). CAS only works on one reference. You need `synchronized` or a compound state object wrapped in `AtomicReference`.

---

### 9e. `ReentrantReadWriteLock` — Concurrent Reads, Exclusive Writes

**The problem it solves:** `synchronized` gives every operation exclusive access. If 90% of operations are pure reads, those readers all block each other unnecessarily.

**How it works:** the lock has two modes:
- **Read lock:** any number of threads can hold it concurrently — as long as no writer holds the write lock
- **Write lock:** exactly one thread. No readers, no other writers. Full exclusion.

```java
private final ReentrantReadWriteLock rwLock    = new ReentrantReadWriteLock();
private final Lock                   readLock  = rwLock.readLock();
private final Lock                   writeLock = rwLock.writeLock();

// Read operation — concurrent with other readers
public String get(String key) {
    readLock.lock();
    try {
        return map.get(key);
    } finally {
        readLock.unlock();
    }
}

// Write operation — exclusive
public void put(String key, String value) {
    writeLock.lock();
    try {
        map.put(key, value);
    } finally {
        writeLock.unlock();
    }
}
```

### 🎨 Visual — The Lock-Upgrade Deadlock Trap

```
  Scenario: get() finds an expired entry and wants to remove it.

  WRONG — deadlock:
  ┌─────────────────────────────────────────────────────────────────┐
  │ Thread A: readLock.lock()    ← holds read lock                  │
  │ Thread B: readLock.lock()    ← holds read lock                  │
  │                                                                  │
  │ Thread A sees expired entry — tries to upgrade:                 │
  │   writeLock.lock()           ← BLOCKS (Thread B holds read)     │
  │                                                                  │
  │ Thread B sees expired entry — tries to upgrade:                 │
  │   writeLock.lock()           ← BLOCKS (Thread A holds read)     │
  │                                                                  │
  │ Both threads hold a read lock, both wait for write lock.        │
  │ Neither will ever release. → DEADLOCK                           │
  └─────────────────────────────────────────────────────────────────┘

  CORRECT — release-then-reacquire:
  ┌─────────────────────────────────────────────────────────────────┐
  │ readLock.unlock();        ← give up read lock FIRST             │
  │ writeLock.lock();         ← now acquire write lock              │
  │                                                                  │
  │ // MANDATORY re-check — another thread may have acted while     │
  │ // we had no lock                                               │
  │ if (map.containsKey(key) && isExpired(map.get(key))) {          │
  │     map.remove(key);                                            │
  │ }                                                               │
  │ writeLock.unlock();                                             │
  └─────────────────────────────────────────────────────────────────┘

  KEY INVARIANT: Java's ReentrantReadWriteLock does NOT support
  read → write upgrade. Always: unlock read, lock write, re-check.
  (Downgrade write → read IS supported, but rarely needed.)
```

**Fair vs Unfair mode:**

| Mode | Behavior | When to use |
|---|---|---|
| Unfair (default) | New readers can barge ahead of a waiting writer | Reads are latency-sensitive; writer starvation is acceptable |
| Fair (`new ReentrantReadWriteLock(true)`) | FIFO ordering — writer never starves | Reads and writes both need guarantees; accept ~20-30% lower reader throughput |

**Cross-Q: "What's the throughput difference vs `synchronized`?"**
For 90% read workload: `ReadWriteLock` allows ~10x throughput (concurrent reads). For 50/50: minimal benefit. For write-heavy: no benefit — the write lock gives the same exclusion as `synchronized`.

**Cross-Q: "When would you use `synchronized` instead of `ReadWriteLock` even for a cache?"**
When every "read" operation mutates state. In the KV Store, `getAverage()` calls `evictExpired()` which modifies the DLL and HashMap. So even reads need write access — `ReadWriteLock` gives no advantage. Use `synchronized`, and note the upgrade path.

**Cross-Q: "Why not `StampedLock`?"**
`StampedLock` supports optimistic reads (no blocking at all — validate a stamp after reading). Faster than `ReadWriteLock` in low-contention cases. But: not reentrant, upgrade semantics are subtle, harder to use correctly in an interview. Not worth it.

---

### 9f. `BlockingQueue` — Producer-Consumer Without Manual `wait/notify`

**The problem it solves:** a producer generates work; a consumer processes it. Without a buffer, they have to stay perfectly in sync. `BlockingQueue` is a thread-safe buffer with built-in blocking — `put()` waits when full, `take()` waits when empty. No `synchronized`, no `wait()`, no `notifyAll()` needed.

**Which variant to pick:**

| Queue | Bounded? | Ordering | Notes |
|---|---|---|---|
| `LinkedBlockingQueue(capacity)` | Yes | FIFO | `put()` blocks when full — natural back-pressure |
| `LinkedBlockingQueue()` | No (Integer.MAX_VALUE) | FIFO | Effectively unbounded — `put()` never blocks |
| `PriorityBlockingQueue` | **No (unbounded)** | Priority | `put()` NEVER blocks. `take()` blocks when empty. Use a Comparator. |
| `ArrayBlockingQueue(capacity)` | Yes | FIFO | Contiguous memory; slightly faster than LinkedBlockingQueue for small sizes |

⚠️ **Critical:** `PriorityBlockingQueue` is always unbounded. Do NOT claim `put()` blocks. If you need bounded priority queuing, reject at the submit layer (before `put()`).

**Dispatcher template:**

```java
private final PriorityBlockingQueue<Task> queue = new PriorityBlockingQueue<>();
private volatile boolean running = false;

public void start() {
    running = true;
    // Single dispatcher thread — preserves priority ordering
    Thread dispatcher = new Thread(() -> {
        while (running) {
            try {
                // poll with timeout so we can check 'running' on shutdown
                Task task = queue.poll(100, TimeUnit.MILLISECONDS);
                if (task != null) {
                    workerPool.submit(task::execute);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    });
    dispatcher.setDaemon(true);
    dispatcher.start();
}
```

**Cross-Q: "Why single dispatcher instead of N workers all calling `take()`?"**
N workers competing on `take()` could dequeue tasks in non-priority order — thread scheduling determines which worker gets the CPU next, not priority. A single dispatcher is the only thread that dequeues, so it always picks the highest-priority task and then hands it to a worker pool.

**Cross-Q: "Why `poll(timeout)` instead of `take()`?"**
`take()` blocks forever. If `running` becomes false while the dispatcher is blocked in `take()`, it never wakes up. `poll(timeout)` returns `null` after the timeout, so the dispatcher re-checks `running` and exits cleanly.

---

### 9g. `ExecutorService` — Thread Pool

**What it gives you:** a pool of worker threads. You submit tasks (`Runnable` or `Callable`); the pool queues them and runs them on available threads. You don't manage thread lifecycle.

```java
// N concurrent workers; tasks beyond N queue up
ExecutorService workerPool = Executors.newFixedThreadPool(4);

// Only one thread — tasks run sequentially in submission order
ExecutorService dispatcher = Executors.newSingleThreadExecutor();

// Submit a Runnable (fire-and-forget)
workerPool.submit(() -> processTask(task));

// Submit a Callable (get a result back via Future)
Future<Integer> result = workerPool.submit(() -> compute());
int value = result.get(); // blocks until done

// Graceful shutdown: wait for in-flight tasks to finish
workerPool.shutdown();

// Immediate shutdown: interrupt in-flight tasks, return waiting tasks
workerPool.shutdownNow();
```

**Daemon threads:** background threads (like the dispatcher) that don't prevent JVM shutdown. Set with `thread.setDaemon(true)` before starting. `ExecutorService` threads are non-daemon by default — pass a `ThreadFactory` to make them daemon if needed.

---

### 9h. Deadlock — How It Happens and How to Prevent It

**Deadlock** requires all four of these conditions simultaneously:
1. **Mutual exclusion** — at least one resource is held exclusively
2. **Hold and wait** — a thread holds one resource while waiting for another
3. **No preemption** — resources can't be taken away; only released voluntarily
4. **Circular wait** — Thread A waits for B's lock; Thread B waits for A's lock

**Fix 1 — Consistent lock ordering:**

```java
// DEADLOCK: Thread A locks user then account; Thread B locks account then user
// FIX: always lock in the same order (lower ID first)
public void transfer(Account from, Account to, int amount) {
    Account first  = from.id < to.id ? from : to;
    Account second = from.id < to.id ? to   : from;
    synchronized (first) {
        synchronized (second) {
            from.balance -= amount;
            to.balance   += amount;
        }
    }
}
```

**Fix 2 — `tryLock(timeout)`:** try to acquire without blocking forever; if timeout, release everything and retry.

**The lock-upgrade deadlock** (most common in Confluent interviews): two threads each hold a read lock and both try to upgrade to write lock. Fix: always `readLock.unlock()` before `writeLock.lock()`, then re-check the condition.

---

### 9i. Confluent Cross-Question Cheat Sheet

These are the verbatim questions you'll hear and the answer in one sentence:

| Question | Answer |
|---|---|
| "Why `synchronized` not `ReadWriteLock` for the KV Store?" | Every public method calls `evictExpired()` which mutates state — so "reads" need write access too. ReadWriteLock only helps when reads are truly read-only. |
| "Does `synchronized` guarantee visibility?" | Yes — JMM guarantees writes inside a `synchronized` block are visible to any thread that subsequently acquires the same monitor (the object's built-in lock). |
| "Can you upgrade a read lock to a write lock?" | No — deadlock. Two readers both trying to upgrade block each other. Release the read lock first, acquire write, then re-check the condition. |
| "What's the throughput gain from ReadWriteLock vs synchronized?" | ~10x for 90% read workloads (concurrent readers). No benefit for write-heavy or when reads have side-effects. |
| "Why is unfair mode the default for ReadWriteLock?" | Reader throughput — new readers can barge ahead of waiting writers. Fair mode prevents writer starvation but costs ~20-30% reader throughput. |
| "Why single dispatcher and not N workers calling `take()`?" | Strict priority ordering. N workers competing on `take()` let thread scheduling decide who dequeues next, not priority. |
| "Why `PriorityBlockingQueue` and not a locked `PriorityQueue`?" | `PriorityBlockingQueue` is thread-safe and `take()` blocks automatically — no `wait/notify` needed. A locked `PriorityQueue` requires manual coordination. |
| "Why `volatile boolean running` and not `AtomicBoolean`?" | Single writer (main thread), multiple readers (workers) — `volatile` is sufficient for visibility. `AtomicBoolean` adds CAS overhead with no benefit here. |
| "Why is CAS faster than synchronized at low contention?" | CAS is a single CPU instruction — no OS lock, no context switch. `synchronized` on contended paths suspends threads (microseconds of overhead). |
| "When is CAS not enough?" | When you need to update two fields atomically (e.g., `runningSum` and `activeCount` together). CAS is single-field only. |
| "Why not StampedLock?" | Optimistic reads are faster but not reentrant; upgrade semantics are subtle. Not worth the complexity in an interview setting. |

**Extended patterns** (Semaphore, CountDownLatch, CyclicBarrier, wait/notify, CopyOnWriteArrayList): [`LLD/concurrency-deep-dive.md`](../../../LLD/concurrency-deep-dive.md) — Patterns 1-8

---

### 9j. Quick-Pick Table — What to Reach For (read this after 9a–9i)

| When you see this... | Reach for this... | Key rule |
|---|---|---|
| Single counter increment/decrement | `AtomicInteger` | `incrementAndGet()` / `getAndAdd()` |
| State machine (PENDING → RUNNING) | `AtomicReference` + CAS | `compareAndSet(old, new)` — only one thread wins |
| One writer, many readers (boolean flag) | `volatile` | Visibility only — NOT safe for read-modify-write |
| Read-then-write must be atomic | `synchronized` block | Guarantees mutual exclusion + visibility |
| Reads >> writes, truly read-only reads | `ReentrantReadWriteLock` | Never upgrade read→write directly (deadlock!) |
| Priority-ordered producer-consumer queue | `PriorityBlockingQueue` | `put()` never blocks (unbounded); `take()` blocks when empty |
| Bounded producer-consumer queue | `LinkedBlockingQueue(cap)` | `put()` blocks when full; `take()` blocks when empty |
| Thread pool for parallel work | `ExecutorService` | `newFixedThreadPool(n)` / `newSingleThreadExecutor()` |
| Shutdown flag for background threads | `volatile boolean` | Only one writer (main thread), many readers (workers) |

---

## 🧾 TL;DR — Pre-Problem Checklist

Before starting any Confluent DSA problem, confirm you can answer these from memory:

- [ ] How does a HashMap resolve collisions? What's the worst-case lookup?
- [ ] Why does LRU Cache need BOTH a HashMap and a DLL?
- [ ] What's the backtracking template? (choose → explore → unchoose)
- [ ] How do you compute subarray sum in O(1) using prefix sums?
- [ ] How does `RandomAccessFile.seek()` differ from `BufferedReader`?
- [ ] What's the 2D DP table structure for matching two strings?
- [ ] Which concurrency primitive do you reach for when reads >> writes?
- [ ] Can you upgrade a read lock to a write lock? (No — deadlock. Release read, acquire write, re-check.)
- [ ] Why does `PriorityBlockingQueue.put()` never block? (Unbounded — reject at submit layer if you need limits.)
- [ ] Why single dispatcher instead of N workers polling the queue?

If any answer is fuzzy, read §9 of this file before touching the concurrency problems.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 2026 | File created. 9 concept sections covering all prerequisites for the Confluent DSA problem set. |
| Jul 2026 | **Deep expansion pass.** All "Full coverage" refs made into clickable relative links. Three sections expanded with original deep content not covered elsewhere: §6 Stack-Based Parsing (push-map-pop-multiply pattern with LC 726 trace + template code), §7 File I/O (Java I/O class comparison table + edge cases), §8 2D String DP (wildcard/regex transition rules + DP table visual + space optimization + 4-problem comparison table). |
| Aug 2026 | **§9 full expansion — Concurrency from zero to interview-ready.** Replaced the 9-row quick-reference table with a self-contained deep dive: root problems (atomicity, visibility + race condition visual), `synchronized` (monitor, reentrant, JMM visibility guarantee), `volatile` (visibility only, when not enough), `AtomicInteger`/`AtomicReference` + CAS (state machine template, contention trade-off), `ReentrantReadWriteLock` (fair/unfair, read→write deadlock visual + correct unlock-reacquire-recheck pattern), `BlockingQueue` variants (`PriorityBlockingQueue` is unbounded — put never blocks, dispatcher template with poll timeout for clean shutdown), `ExecutorService` (fixed pool, single thread, daemon threads, shutdown vs shutdownNow), deadlock (4 conditions, lock ordering fix), and Confluent cross-question cheat sheet (11 ready answers matching every question in `04-concurrency-problems.md`). |
