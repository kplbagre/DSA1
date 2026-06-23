# DSA Knowledge Base — Reorganization Plan

> **Status:** PENDING — do not start execution without reading this file top-to-bottom first.
> **Last updated:** June 2026
> **Context:** Planned after auditing all existing files and identifying structural gaps that cause "I don't know where to start after 2 months" and "I keep forgetting DS syntax" problems.

---

## 🎯 Problem Being Solved

The current structure has three compounding issues:

1. **No entry point for returning learners.** Coming back after 2 months, there is no file that says "here are all the data structures, when to use each, how Java implements them." The structure assumes you already know DSA and dives straight into patterns.

2. **Reference files are doing DeepDive's job.** Five DeepDive files are missing (stacks/queues, linked list, strings, hashmaps/sets, heaps). Because they don't exist, the Reference files grew to 500-800 lines to fill the teaching gap — way past their 300-600 line target. They now teach AND revise, doing neither cleanly.

3. **Reference is partially redundant with Playbooks.** Every Interview Playbook has an "Essential Methods" table which duplicates what Reference was supposed to own. Result: Reference files are consulted rarely. When you open a Playbook for an interview, the methods are already there.

---

## 🗺️ Target State (after all steps complete)

```
DSA/
├── AGENTS.md                              ← UPDATE (add Foundation folder)
│
├── Foundation/                            ← NEW FOLDER — entry point for returning learners
│   ├── ds-overview.md                     ← NEW — all DSes, when to use each, trade-offs
│   ├── java-collections-visual.md         ← NEW — Java hierarchy + backing structures
│   └── syntax-quick-card.md              ← NEW — creation syntax + top 5 methods per DS
│
├── DeepDive/                              ← EXPAND (5 missing files)
│   ├── notes-standards-deepdive.md        ← no change
│   ├── trees-fundamentals.md              ← no change
│   ├── recursion-fundamentals.md          ← no change
│   ├── graphs-fundamentals.md             ← no change
│   ├── backtracking-fundamentals.md       ← no change
│   ├── dp-fundamentals.md                 ← no change
│   ├── arrays-fundamentals.md             ← no change
│   ├── two-pointers-sliding-window-fundamentals.md ← no change
│   ├── integer-overflow-and-limits.md     ← no change
│   ├── hybrid-design-problems.md          ← no change
│   ├── stacks-queues-fundamentals.md      ← NEW
│   ├── linked-list-fundamentals.md        ← NEW
│   ├── strings-fundamentals.md            ← NEW
│   ├── hashmaps-sets-fundamentals.md      ← NEW
│   └── heaps-fundamentals.md             ← NEW
│
├── Reference/                             ← TRIM + DELETE (see per-file actions)
│   ├── notes-standards-reference.md       ← no change
│   ├── interview-morning-cheatsheet.md    ← no change (correctly scoped)
│   ├── lambdas-for-dsa-reference.md       ← no change (unique content)
│   ├── code-style-for-dsa-reference.md    ← no change (unique content)
│   ├── bfs-dfs-templates-reference.md     ← no change (used as templates library by Playbooks)
│   ├── arrays-reference.md                ← TRIM to ~350 lines
│   ├── stacks-queues-reference.md         ← RENAME + TRIM (currently arraydeque-and-queue-reference.md)
│   ├── hashmap-reference.md               ← MERGE + TRIM (hashmap + set → one file, ~350 lines)
│   ├── trees-reference.md                 ← TRIM to ~350 lines
│   ├── linked-list-reference.md           ← TRIM to ~250 lines
│   └── strings-reference.md              ← TRIM to ~250 lines (rename string-operations-reference.md)
│
│   DELETE (after DeepDive files created):
│   ├── dsa-collections-notes.md           ← DELETE (older redundant version of hashmap+set)
│   ├── set-section-updated.md             ← DELETE (merge into hashmap-reference.md)
│   ├── hashmap-section-updated.md         ← DELETE (replaced by trimmed hashmap-reference.md)
│   └── two-pointers-sliding-window-reference.md ← DELETE (Playbook covers this fully)
│
├── Implementation/                        ← no change
├── Interview/                             ← no change
│   └── Playbooks/                         ← no change
├── Patterns/                              ← no change
└── Practice/                              ← EXPAND (add DrillQuestions subfolder)
    ├── README.md                          ← no change
    ├── DrillQuestions/                    ← NEW SUBFOLDER
    │   ├── README-drills.md               ← NEW — drill protocol for Q&A sessions
    │   ├── hashmaps-sets-drills.md        ← NEW — question bank
    │   ├── arrays-drills.md               ← NEW — question bank
    │   ├── stacks-queues-drills.md        ← NEW — question bank
    │   ├── trees-drills.md                ← NEW — question bank
    │   ├── linked-list-drills.md          ← NEW — question bank
    │   ├── strings-drills.md              ← NEW — question bank
    │   └── heaps-drills.md               ← NEW — question bank
    └── (existing .java files unchanged)
```

---

## 📋 Execution Steps (in order — dependencies noted)

### Step 1 — Create `Foundation/` folder (3 files)

**Dependencies:** None. Self-contained. Do this first — fixes the "2-month gap" immediately.

**Step 1a — `Foundation/ds-overview.md`**

Purpose: Orient yourself in 15 minutes after any gap. No patterns, no code — just "what exists and when to choose it."

Content to include:
- Introduction: "Read this before anything else when returning after a gap."
- **Section per category** (8 categories):
  1. **Linear** — Array (primitive `int[]`), ArrayList, LinkedList (when to use each, key difference)
  2. **Stack / Queue** — ArrayDeque as stack, ArrayDeque as queue, when FIFO vs LIFO
  3. **Priority** — PriorityQueue (min-heap), when to use (Top K, Dijkstra, scheduling)
  4. **Maps** — HashMap vs LinkedHashMap vs TreeMap (the 3-way comparison is the key)
  5. **Sets** — HashSet vs LinkedHashSet vs TreeSet (mirrors Map choices)
  6. **Tree** — TreeNode (custom), BST properties, when tree vs graph
  7. **Graph** — Adjacency list vs adjacency matrix (when to use each)
  8. **Specialized** — Trie (string prefix problems), Union-Find/DSU (connected components)

- **Cross-DS decision table** (the most important part — this is what you need after 2 months):

  | You need... | Use | NOT | Reason |
  | --- | --- | --- | --- |
  | O(1) lookup by key | HashMap | TreeMap | TreeMap is O(log n) — only use when sorted order needed |
  | Sorted key order | TreeMap | HashMap | Tree-backed, O(log n) but ordered |
  | Insertion order preserved | LinkedHashMap | HashMap | Extra linked list tracks insertion order |
  | O(1) membership check | HashSet | List + contains | List.contains is O(n) scan |
  | Sorted unique elements | TreeSet | HashSet | Tree-backed, sorted iteration |
  | FIFO (queue) | ArrayDeque | LinkedList | ArrayDeque is faster, less memory |
  | LIFO (stack) | ArrayDeque | java.util.Stack | Stack is legacy, synchronized (slow) |
  | Best-so-far / priority | PriorityQueue | sorted array | O(log n) insert vs O(n) insertion sort |
  | Frequent insert/delete at middle | LinkedList | ArrayList | ArrayList shifts O(n) on insert/delete |
  | Index-based access | ArrayList | LinkedList | LinkedList is O(n) to reach index i |
  | Range queries on keys | TreeMap | HashMap | .subMap(), .headMap(), .tailMap() |
  | Prefix string matching | Trie | HashMap | Trie shares common prefixes, O(L) per op |
  | Connected component grouping | Union-Find | BFS/DFS repeated | DSU is O(α(n)) ≈ O(1) per union |

- **Complexity quick reference** per DS (creation, access, insert, delete, search)

Length target: 250-350 lines. Dense but scannable — tables, not prose.

---

**Step 1b — `Foundation/java-collections-visual.md`**

Purpose: Understand WHY each DS has its complexity. The backing structure explains everything.

Content to include:
- **Full Java `java.util` hierarchy (ASCII):**
  ```
  Collection<E>
  ├── List<E>
  │   ├── ArrayList      ← backed by Object[] (dynamic array)
  │   └── LinkedList     ← backed by doubly linked node chain
  │
  ├── Queue<E>
  │   ├── Deque<E>
  │   │   ├── ArrayDeque ← backed by Object[] (circular array) — DEFAULT for stack/queue
  │   │   └── LinkedList ← also implements Deque, but slower than ArrayDeque
  │   └── PriorityQueue  ← backed by Object[] (binary min-heap) — NOT a Deque
  │
  └── Set<E>
      ├── HashSet        ← backed by HashMap (hash table: array of buckets)
      ├── LinkedHashSet  ← backed by LinkedHashMap (hash table + doubly linked list)
      └── TreeSet        ← backed by TreeMap (red-black tree)

  Map<K,V>              ← separate hierarchy, NOT Collection
  ├── HashMap            ← hash table: array of bucket chains / tree nodes
  ├── LinkedHashMap      ← hash table + doubly linked list for insertion order
  └── TreeMap            ← red-black tree (self-balancing BST), sorted by key
  ```

- **Internal structure per DS** — one paragraph per DS explaining the backing structure and why it produces the complexity it does:
  - HashMap: hash table with buckets → why O(1) avg, O(n) worst
  - TreeMap: red-black tree → why O(log n) guaranteed
  - PriorityQueue: binary heap → why poll/offer O(log n) but peek O(1)
  - ArrayDeque: circular array → why all operations O(1) amortized
  - etc.

- **Custom DS used in DSA problems (not in java.util):**
  - `TreeNode` — binary tree node (LeetCode pre-defines this)
  - `ListNode` — singly linked list node (LeetCode pre-defines this)
  - `int[] parent, int[] rank` — Union-Find DSU (you implement this)
  - Adjacency list: `List<List<Integer>> adj = new ArrayList<>()`
  - Adjacency matrix: `int[][] mat = new int[n][n]`

Length target: 250-350 lines. Visual-heavy. ASCII diagrams are the main content.

---

**Step 1c — `Foundation/syntax-quick-card.md`**

Purpose: The 5 methods you blank on after 2 months. Pure syntax, no explanations, no patterns. Open this when you know WHAT to use but can't remember HOW to write it.

Content to include (for each DS — creation line + 5 most-forgotten methods):
- Array (`int[]`)
- ArrayList
- LinkedList (as list, not queue)
- ArrayDeque as Stack
- ArrayDeque as Queue
- PriorityQueue (min-heap, max-heap, custom comparator)
- HashMap (creation + getOrDefault, merge, computeIfAbsent, entrySet iteration)
- LinkedHashMap
- TreeMap (creation + floorKey, ceilingKey, firstKey, lastKey, subMap)
- HashSet
- TreeSet (creation + first, last, floor, ceiling, higher, lower)
- StringBuilder (append, insert, delete, reverse, toString)
- String (charAt, substring, indexOf, split, toCharArray, compareTo)
- Arrays utility (sort, fill, copyOf, equals, binarySearch)
- Collections utility (sort, reverse, min, max, frequency, unmodifiableList)

Length target: 200-280 lines. One code block per DS — creation + methods only, no prose.

---

### Step 2 — Create 5 missing `DeepDive/` files

**Dependencies:** None — create independently. But finish these BEFORE Step 3 (trimming Reference), because Reference trim relies on these existing.

**CRITICAL RULE for each file:** Once these DeepDive files exist, the corresponding Reference file can be trimmed. Do NOT trim a Reference file until its DeepDive file is complete.

---

**Step 2a — `DeepDive/hashmaps-sets-fundamentals.md`**

Why first: HashMap/HashSet are used in almost every other pattern. Understanding them deeply unlocks everything else.

Content sourced from (READ BEFORE WRITING):
- `Reference/hashmap-section-updated.md` — currently doing teaching job
- `Reference/set-section-updated.md` — currently doing teaching job
- `Reference/dsa-collections-notes.md` — older reference

Topics to cover (follow `notes-standards-deepdive.md` conventions):
- §Terminology: hash table, bucket, collision, load factor, hash function, chaining
- §Mental model: HashMap is a phone book — O(1) by key, blind to order
- §How HashMap works internally (hashing → bucket → chain → tree at 8 entries)
- §All HashMap methods (full table, ALL 15 methods with time complexity)
- §Common DSA patterns for HashMap (with English steps + code):
  - Frequency map
  - Two Sum (complement lookup)
  - Group Anagrams (canonical key)
  - Prefix Sum + HashMap
  - Sliding Window (window shrink)
  - First non-repeating character (LinkedHashMap)
- §HashSet: when to use Set vs Map
- §Common DSA patterns for HashSet
- §TreeMap: when sorted order matters
- §TreeSet: when sorted unique matters
- §Gotchas (mutable keys, Integer vs int boxing, equals/hashCode contract)
- §Inline drills after each section (per AGENTS.md drill convention)
- §Try these problems (with ✅/🟡/🔴 tags)

Length target: 900-1200 lines (this is a dense topic — two data structures).

---

**Step 2b — `DeepDive/stacks-queues-fundamentals.md`**

Content sourced from (READ BEFORE WRITING):
- `Reference/arraydeque-and-queue-reference.md` — currently 518 lines doing teaching job
- Cross-reference `Interview/Playbooks/stacks-and-queues.md` for patterns

Topics to cover:
- §Terminology: LIFO, FIFO, deque, monotonic stack, call stack
- §Mental model: Stack = "things I haven't dealt with yet." Queue = "things waiting to be processed."
- §Java Collections hierarchy for Queue/Deque (the ASCII diagram from arraydeque-and-queue-reference.md belongs HERE, not in Reference)
- §ArrayDeque as Stack — push/pop/peek, full method table
- §ArrayDeque as Queue — offer/poll/peek, full method table
- §ArrayDeque as Deque — addFirst/addLast/removeFirst/removeLast
- §PriorityQueue — binary heap, min-heap vs max-heap, custom comparator
- §Why NEVER use java.util.Stack
- §Common DSA patterns:
  - Bracket matching
  - Monotonic stack (next greater element)
  - Stack-based expression evaluation
  - Min stack design
  - BFS queue usage
- §Inline drills
- §Try these problems

Length target: 700-900 lines.

---

**Step 2c — `DeepDive/linked-list-fundamentals.md`**

Content sourced from (READ BEFORE WRITING):
- `Reference/linkedlist-reference.md` — currently 594 lines doing teaching job
- `Interview/Playbooks/linked-list.md` for pattern context

Topics to cover:
- §Terminology: node, head, tail, singly linked, doubly linked, sentinel/dummy
- §Mental model: a linked list is pointer surgery — you can't random-access, you must walk
- §ListNode class (LeetCode pre-defines, but write it out)
- §Three questions for any linked list problem (one-pass vs two-pass, dummy head, in-place vs new list)
- §Core pointer patterns (with ASCII diagrams):
  - The reversal (three-pointer dance: prev/curr/next)
  - The dummy head
  - Floyd's slow/fast
  - The k-gap pointer
- §Common DSA patterns:
  - Cycle detection (Floyd's)
  - Finding middle
  - Reversing in-place
  - Merging sorted lists
  - Reordering (LC 143)
  - Remove Nth from end (k-gap)
- §Gotchas (lose reference to next before reassigning, dummy head prevents null checks)
- §Inline drills
- §Try these problems

Length target: 600-800 lines.

---

**Step 2d — `DeepDive/strings-fundamentals.md`**

Content sourced from (READ BEFORE WRITING):
- `Reference/string-operations-reference.md` — currently 714 lines doing teaching job
- `Interview/Playbooks/strings.md` for pattern context

Topics to cover:
- §Terminology: immutable, interning, char array, StringBuilder, character class
- §Mental model: Strings are immutable — every concatenation creates a new object. Use StringBuilder for loops.
- §String vs char[] — when to convert (sort, modify, compare)
- §Core String methods (full table: charAt, substring, indexOf, lastIndexOf, contains, startsWith, endsWith, split, trim, replace, toLowerCase, toUpperCase, compareTo, equals, isEmpty, length)
- §StringBuilder (append, insert, delete, deleteCharAt, reverse, charAt, length, toString)
- §char operations (Character.isDigit, isLetter, isAlphabetic, isUpperCase, isLowerCase, toLowerCase, toUpperCase, digit-to-int: `c - '0'`, char-to-index: `c - 'a'`)
- §Common DSA patterns (with English steps + code):
  - Frequency counting with int[26] array (anagram check)
  - Sliding window on strings
  - Palindrome check (two pointers + expand from center)
  - String building in loops (StringBuilder)
  - Parsing: split + trim + Integer.parseInt
  - Canonical key for anagram grouping (sort chars)
- §Gotchas (== vs .equals(), String pool, substring memory leak in older Java)
- §Inline drills
- §Try these problems

Length target: 700-900 lines.

---

**Step 2e — `DeepDive/heaps-fundamentals.md`**

Content sourced from (READ BEFORE WRITING):
- `Interview/Playbooks/heaps.md` — covers patterns, check what's already there before writing
- No existing Reference file for heaps — must build from scratch

Topics to cover:
- §Terminology: heap, min-heap, max-heap, heapify, sift-up, sift-down, priority queue
- §Mental model: a PriorityQueue is a sorting machine that only exposes the best element — you never see the full sorted order, only the current winner
- §How binary heap works internally (array-backed, parent at `(i-1)/2`, children at `2i+1` and `2i+2`)
- §Java PriorityQueue creation (min-heap default, max-heap with `Collections.reverseOrder()`, custom comparator)
- §Full method table: offer/add, poll, peek, size, isEmpty, remove(Object)
- §Common DSA patterns:
  - Kth largest / Kth smallest (fixed-size heap)
  - Top K frequent elements
  - Merge K sorted lists
  - Sliding window maximum
  - Dijkstra (priority queue core)
  - Meeting rooms II (two PQs)
- §When heap vs sort (heap = O(n log k), sort = O(n log n) — heap wins for streaming/online K problems)
- §Gotchas (offer vs add — both add, but add throws on capacity limit; PQ does NOT iterate in sorted order)
- §Inline drills
- §Try these problems

Length target: 700-900 lines.

---

### Step 3 — Trim `Reference/` files to true revision purpose

**Dependencies:** Each Reference file can only be trimmed AFTER its corresponding DeepDive file is complete (Step 2). Check the dependency for each.

**The rule for trimming:** Remove anything that TEACHES (long prose, mental models, internal structure explanations, long worked examples). Keep only what REVISES (method tables, quick pattern code snippets, gotchas checklist).

---

**Step 3a — Trim `arrays-reference.md` (currently 767 lines → target ~350 lines)**

Dependency: `arrays-fundamentals.md` already exists in DeepDive ✅ — can trim immediately.

What to KEEP:
- The 3-question mental model (short, revision-friendly)
- The 14-pattern quick list (pattern name + 1-line trigger + code template)
- Gotchas section
- Quick cheat sheet at end

What to REMOVE:
- Long prose explanations before each pattern
- Any section that reads like "first let me explain..." — that belongs in DeepDive

---

**Step 3b — Trim `stacks-queues-reference.md` (currently arraydeque-and-queue-reference.md, 518 lines → target ~250 lines)**

Dependency: Must wait for Step 2b (`stacks-queues-fundamentals.md`).

What to KEEP:
- Decision matrix table (FIFO vs LIFO vs priority vs index access) — this is gold
- Method tables for ArrayDeque (as stack: push/pop/peek) and (as queue: offer/poll/peek)
- PriorityQueue method table + min/max comparator snippets

What to REMOVE:
- The Java Collections hierarchy diagram → MOVE to `stacks-queues-fundamentals.md` (DeepDive) and `java-collections-visual.md` (Foundation)
- The "where does ArrayDeque fit" teaching paragraphs → DeepDive
- Long explanations of why ArrayDeque beats LinkedList → DeepDive

Also RENAME: `arraydeque-and-queue-reference.md` → `stacks-queues-reference.md` for consistency.

---

**Step 3c — Create `hashmap-reference.md` and DELETE 3 old files**

Dependency: Must wait for Step 2a (`hashmaps-sets-fundamentals.md`).

Action: Create a NEW `hashmap-reference.md` (~350 lines) that is the merged, trimmed revision file for HashMap + HashSet + their variants.

What the new file contains:
- HashMap creation + top 8 methods table (getOrDefault, merge, computeIfAbsent, entrySet are the critical ones)
- HashSet creation + top 5 methods table
- TreeMap creation + floor/ceiling/first/last methods
- TreeSet creation + same
- Iteration patterns (entrySet, keySet, for-each on Set)
- Quick cheat sheet: HashMap vs LinkedHashMap vs TreeMap decision (1 table, 3 rows)

After creating `hashmap-reference.md`, DELETE:
- `hashmap-section-updated.md` (superseded)
- `set-section-updated.md` (merged in)
- `dsa-collections-notes.md` (older redundant version)

---

**Step 3d — Trim `trees-reference.md` (currently 708 lines → target ~350 lines)**

Dependency: `trees-fundamentals.md` already exists in DeepDive ✅ — can trim immediately.

What to KEEP:
- TreeNode canonical class snippet
- Traversal quick table (preorder/inorder/postorder/BFS — when to use each, time, space)
- All 4 traversal code templates (recursive DFS variants + BFS)
- BST operations cheat sheet (insert, search, delete — when needed)
- Pattern quick list (bottom-up, top-down, LCA, BST)
- Gotchas

What to REMOVE:
- Long mental model prose — keep only the one-liner hook
- "Why this works" explanations → DeepDive

---

**Step 3e — Trim `linkedlist-reference.md` → `linked-list-reference.md` (currently 594 lines → target ~250 lines)**

Dependency: Must wait for Step 2c (`linked-list-fundamentals.md`).

What to KEEP:
- ListNode class snippet
- The 3-question framework (1 pass vs 2 pass, dummy head, in-place vs new)
- Pattern templates (reversal, slow/fast, dummy head, k-gap) — code only, no prose explanation
- Gotchas

What to REMOVE:
- Teaching prose — DeepDive owns it now
- Full pattern explanations with walkthroughs — Playbook owns it

---

**Step 3f — Trim `string-operations-reference.md` → `strings-reference.md` (currently 714 lines → target ~250 lines)**

Dependency: Must wait for Step 2d (`strings-fundamentals.md`).

What to KEEP:
- String ↔ char[] conversion snippets
- Core String method table (the ones you blank on: charAt, substring, indexOf, split, compareTo)
- StringBuilder method table (append, insert, delete, reverse, toString)
- char operations (isDigit, isLetter, c - '0', c - 'a')
- Pattern code snippets (int[26] frequency, sliding window, palindrome check)

What to REMOVE:
- "Why strings are immutable" explanation → DeepDive
- Long `StringBuilder` vs `String` comparison → DeepDive

Also RENAME: `string-operations-reference.md` → `strings-reference.md`.

---

**Step 3g — DELETE `two-pointers-sliding-window-reference.md`**

Dependency: None — can delete at any time.

Reason: `Interview/Playbooks/two-pointers-and-sliding-window.md` (905 lines) covers all patterns with full code. The Reference file at 480 lines is a duplication that nobody opens because the Playbook is more useful.

Before deleting: verify `two-pointers-and-sliding-window.md` Playbook has the atMost(K) trick, the "worm" animation, and the at-most-K inclusion-exclusion pattern. If any is missing, move it there first, THEN delete the Reference file.

---

### Step 4 — Set up `Practice/DrillQuestions/`

**Dependencies:** None — can do any time. Lower priority than Steps 1-3.

**Purpose:** Interactive Q&A drills. User says "quiz me on HashMaps" → I pull questions from the question bank → user writes answers from memory → compare. Different from the 25-min LeetCode problem drills in Practice/README.md.

**Step 4a — `Practice/DrillQuestions/README-drills.md`**

Content:
- How this works: "say 'quiz me on [topic]' → I pick a question → you write from memory → compare"
- Question difficulty levels: 🟢 (syntax recall), 🟡 (pattern recall), 🔴 (trade-off reasoning)
- Categories available: hashmaps-sets, arrays, stacks-queues, trees, linked-list, strings, heaps

**Step 4b — Per-topic drill files**

Each file: `Practice/DrillQuestions/<topic>-drills.md`
Each question follows this format:
```
**Q[N]: [Question]** | 🟢/🟡/🔴

[The question — specific, testable, small]

<details>
<summary>Answer (reveal after writing)</summary>

[The answer — exact syntax, exact method name, exact code]
</details>
```

Questions per topic (20-30 per file) must cover three categories:
1. **Syntax recall** (🟢): "Write the line to create a HashMap<String, Integer>"
2. **Method name recall** (🟢/🟡): "Which HashMap method inserts only if key is absent?" "Which method increments a count in one line without if/else?"
3. **Trade-off reasoning** (🟡/🔴): "You need O(1) lookup AND sorted key order. What do you use and why can't HashMap work here?"

Topics and sample questions:

`hashmaps-sets-drills.md` (20+ questions):
- Create HashMap, HashSet, TreeMap, TreeSet, LinkedHashMap
- getOrDefault signature + when to use over get
- merge(key, 1, Integer::sum) — what does it do if key absent? if present?
- computeIfAbsent — when to use over putIfAbsent
- Iterate entrySet — write the for-each loop
- HashMap vs LinkedHashMap — difference in one sentence
- HashMap vs TreeMap — when to choose TreeMap
- HashSet vs TreeSet — when to choose TreeSet
- HashSet.add() return value — what does false mean?
- Why can't you use a mutable object as HashMap key?

`arrays-drills.md` (20+ questions):
- Arrays.sort() vs Collections.sort() — when to use each
- Arrays.fill(arr, val) — write it
- Arrays.copyOf(arr, newLength) — write it
- Arrays.equals(a, b) vs a == b — difference
- int[] vs Integer[] — which one Arrays.sort uses natural order on
- Write cyclic sort template from memory
- Write Dutch National Flag (0/1/2 sort) template
- Write Kadane's algorithm template

`stacks-queues-drills.md` (20+ questions):
- Create ArrayDeque as stack — which methods for push/pop/peek?
- Create ArrayDeque as queue — which methods for enqueue/dequeue?
- Why never use java.util.Stack?
- PriorityQueue default order — min or max?
- Write PriorityQueue for max-heap
- Write PriorityQueue with custom comparator (e.g., by second element of int[])
- ArrayDeque.push() vs ArrayDeque.offer() — which end?
- isEmpty() vs size() == 0 — which is preferred?

`trees-drills.md` (20+ questions):
- Write TreeNode class from memory
- Write recursive preorder DFS
- Write recursive inorder DFS
- Write recursive postorder DFS
- Write iterative BFS (level order) — full method
- When to use BFS vs DFS on trees?
- What does inorder traversal of a BST produce?
- Write the 3-condition base case for a null/leaf check

`linked-list-drills.md` (20+ questions):
- Write ListNode class from memory
- Write the 3-pointer reversal (prev/curr/temp) loop
- When do you use a dummy head?
- Write the slow/fast pointer cycle detection
- Write the k-gap pointer for "remove Nth from end"
- What breaks if you forget to save curr.next before overwriting?

`strings-drills.md` (20+ questions):
- String → char[] (write it)
- char[] → String (write it)
- Write int[26] frequency map for a String
- c - 'a' gives what? c - '0' gives what?
- Which String method returns the index of first occurrence of a substring?
- Write the palindrome two-pointer check
- Why is `str += c` in a loop O(n²)? What to use instead?
- StringBuilder.reverse() — returns what? Modifies in place?

`heaps-drills.md` (20+ questions):
- Write min-heap PriorityQueue (default)
- Write max-heap PriorityQueue
- Write PriorityQueue of int[] sorted by second element
- offer() vs add() — difference?
- peek() on empty PQ — returns what?
- poll() on empty PQ — returns what?
- PriorityQueue does NOT support indexed access — true/false?
- When heap vs sort? (streaming K problems — heap O(n log k) vs sort O(n log n))
- Write the "Kth largest in stream" pattern using a fixed-size min-heap

---

### Step 5 — Update `DSA/AGENTS.md`

**Dependencies:** Do after Steps 1-3 are complete so the folder structure is accurate.

Changes needed:
1. Add `Foundation/` folder to the `📁 Folder Structure` section (with 3 files listed)
2. Add `Foundation/` as a sixth note type in the `🧭 The Five Note Types` table:
   - Folder: `Foundation/`
   - Type: **Foundation (orientation)**
   - When to use: Coming back after a gap; need to know what DS exists before diving into patterns
   - Length target: 200-350 lines per file
   - Standards file: (none needed — self-explanatory)
3. Update the `📁 Folder Structure` section to show new DeepDive files and trimmed Reference files
4. Add `DrillQuestions/` under `Practice/`
5. Add a Changelog row: "June 2026 — Major reorganization: Foundation folder added, 5 DeepDive files created, Reference trimmed"

---

### Step 6 — Update `DSA/Interview/index.md`

**Dependencies:** After Step 1 (Foundation files exist).

Add a new section at the TOP of `index.md` (before the decision tree):

```
## 🪜 Before the Decision Tree — Start Here If Returning After a Gap

If you've been away from DSA for more than 2 weeks:
1. Read `DSA/Foundation/ds-overview.md` first (15 min) — what DSes exist + when to use each
2. Read `DSA/Foundation/syntax-quick-card.md` (5 min) — creation syntax you'll blank on
3. Then use the decision tree below to pick your Playbook

Skip Foundation if you remember DSA clearly — the decision tree is your entry point.
```

---

## ⚠️ Things That Will Break If You Forget Them

1. **Don't trim a Reference file before its DeepDive counterpart exists.** Order matters — DeepDive first, Reference trim second. Otherwise you lose the teaching content with nowhere to move it.

2. **Don't delete `two-pointers-sliding-window-reference.md` without verifying the atMost(K) trick is in the Playbook.** Check `two-pointers-and-sliding-window.md` Playbook for: atMost(K) inclusion-exclusion pattern, the "worm animation" (or equivalent), and the at-most vs exactly-K problem type mapping.

3. **The Java Collections hierarchy visual must exist in TWO places:** `java-collections-visual.md` (Foundation — complete view) AND `stacks-queues-fundamentals.md` (DeepDive — focused on Queue/Deque part). When trimming `arraydeque-and-queue-reference.md`, the diagram gets moved to DeepDive, NOT deleted.

4. **Drill questions must be specific and testable.** "Think about HashMap" is a bad drill. "Write the line to increment a count using merge()" is good. Every question must have an exact answer the user can compare against.

5. **After renaming Reference files**, update all cross-references in Playbooks. Every Playbook has a `🔗 Cross-References` table — check that renamed files are updated there.

6. **Practice/DrillQuestions/ files are question banks for ME to pick from, not for the user to open.** The user interacts by saying "quiz me" — I read the question bank and ask one at a time. Don't design the drill files as reading material.

---

## 📊 Effort Estimate Per Step

| Step | Effort | Notes |
| --- | --- | --- |
| Step 1 — Foundation/ (3 files) | Medium (3-4 sessions) | Self-contained, do first |
| Step 2a — hashmaps-sets-fundamentals.md | High (1200 lines) | Two DSes in one file, dense |
| Step 2b — stacks-queues-fundamentals.md | Medium (800 lines) | Hierarchy diagram included |
| Step 2c — linked-list-fundamentals.md | Medium (700 lines) | ASCII pointer diagrams needed |
| Step 2d — strings-fundamentals.md | Medium (800 lines) | Many char operations to cover |
| Step 2e — heaps-fundamentals.md | Medium (800 lines) | Build from scratch, no existing Reference |
| Step 3 — Trim Reference files | Low per file | Mostly deletion, not writing |
| Step 4 — DrillQuestions/ | Medium (7 files × 20 questions) | Do last, lower priority |
| Step 5 — Update AGENTS.md | Low | Mechanical update |
| Step 6 — Update index.md | Low | Add one section |

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| June 2026 | **Plan created.** Full reorganization plan after audit of all 55 existing DSA files. Root cause: 5 missing DeepDive files causing Reference bloat + no Foundation layer for returning learners. |

