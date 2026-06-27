# Tries — Interview Playbook

> **Read this file when:** You see "prefix," "autocomplete," "search words in a grid," or "replace with shortest root." Tries appear regularly at Meta and Google — this is the highest-ROI topic not covered in the original 13 playbooks.

---

## 🎯 Why You're Reading This

A **Trie** (also called a prefix tree — a tree where each path from root to a node spells out a string, making prefix lookups instantaneous) gives you something no other data structure does: O(L) search for any word of length L, regardless of how many words are stored. One million words in the dictionary, but finding a 5-letter prefix takes exactly 5 steps.

After reading this file, you should be able to:
1. Recognize the 5 trie patterns from problem wording alone
2. Build a `TrieNode` from memory (array-backed or map-backed) and know when to choose each
3. Write the DFS + Trie template for multi-word grid search cold — the hardest trie pattern

---

## 🔧 Essential Methods — Know These Cold

| Method | What it does | Used in |
| --- | --- | --- |
| `node.children[c - 'a']` | Navigate to child node for char `c` (array-backed) | All patterns |
| `node.children.get(c)` | Navigate to child node for char `c` (map-backed) | All patterns |
| `node.isEnd = true` | Mark this node as the end of a complete word | Insert |
| `node.word = word` | Store the full word string at leaf (grid search optimization) | Pattern 4 |
| `board[r][c] = '#'` | Mark a grid cell as visited during DFS | Pattern 4 |
| `board[r][c] = ch` | Restore cell after backtracking | Pattern 4 |
| `word.toCharArray()` | Iterate characters of a string | All patterns |

---

## 🔄 TrieNode Design — Two Implementations (Interview Discussion Point)

Know both. Default to array-backed in interviews.

```java
// ─── Array-backed TrieNode (default for lowercase a-z problems) ───
// children[0] = 'a', children[1] = 'b', ..., children[25] = 'z'
// Array index: idx = c - 'a'
// Access: O(1), always allocates 26 slots regardless of actual children
class TrieNode {
    TrieNode[] children = new TrieNode[26];
    boolean isEnd = false;
    // For Pattern 4 (word search), use String word = null instead of isEnd
}

// ─── HashMap-backed TrieNode (flexible — any character set) ───
// Works for uppercase, digits, spaces, Unicode, mixed alphabets
// Slightly more overhead per lookup, uses less memory for sparse tries
class TrieNode {
    Map<Character, TrieNode> children = new HashMap<>();
    // 🔄 Fallback for array-backed: TrieNode[] children = new TrieNode[26];
    boolean isEnd = false;
}
```

> **Interview rule of thumb:** Default to `TrieNode[26]` with `c - 'a'` — it's faster to write and has cleaner code. If the problem says "can contain uppercase, digits, or spaces" → switch to `HashMap<Character, TrieNode>`. Mention this tradeoff if the interviewer asks about your design.

---

## 🧠 The Mental Model — When to Think "Trie"

```
What is the problem asking about?
│
├── "Store words and check if exact word exists" + also need prefix queries
│   └── Patterns 1+2: Insert + Search + startsWith
│       (If you only need exact lookup with no prefix → use HashSet instead)
│
├── "Search with wildcard characters (. matches any letter)"
│   └── Pattern 3: Wildcard DFS — recursion when you hit '.'
│
├── "Find ALL words from a word list simultaneously in a 2D grid"
│   └── Pattern 4: DFS + Trie ⭐ (Meta/Google favorite)
│       (Single word in grid → backtracking.md plain DFS, no trie needed)
│
├── "Replace/find the shortest dictionary root that prefixes a word"
│   └── Pattern 5: Shortest Prefix Walk — stop at first isEnd node
│
└── "Just need to check if string is in a set" → don't use trie, use HashSet
```

### 🎨 Visual — What a Trie Looks Like

Words inserted: `"app"`, `"apple"`, `"apply"`, `"apt"`

```
           root
            │
            a          ← index 0 ('a' - 'a' = 0)
            │
            p          ← index 15 ('p' - 'a' = 15)
           ╱ ╲
          p    t [●]   ← "apt" ends here (isEnd = true)
         [●]           ← "app" ends here (isEnd = true)
         ╱
        l
       ╱ ╲
      e    y
     [●]  [●]          ← "apple" and "apply" end here

Traversal to check "apple":
  root → a → p → p → l → e → isEnd? YES ✓

Traversal to check "app":
  root → a → p → p → isEnd? YES ✓  (different node from "apple")

Traversal for startsWith("ap"):
  root → a → p → reached end of prefix → true ✓  (no isEnd check!)

Attempt to search "apricot":
  root → a → p → r?  → children['r'-'a'] == null → false ✗ (prune here)

KEY INVARIANT:
   Every edge = one character. Every path root→[●] = exactly one stored word.
   Every prefix of a stored word has a corresponding node in the trie.
   Lookup cost = O(L) for any L-length word, independent of vocabulary size.
```

---

## 🧭 Pattern 1: Insert + Search ⭐

**What this solves:** Build a dictionary of words and check whether an exact word was previously inserted. You need both exact-word lookup AND prefix queries — if only exact lookup is needed, a `HashSet` is simpler.

**Recognition cues — reach for this when:**
- "Implement a Trie with insert, search, and startsWith operations"
- Problem explicitly requires checking both full words AND prefixes
- Dictionary-style lookup where you need to query by prefix

**Brute force:** Store all words in a `HashSet<String>`. Exact search is O(1) average — fast! But prefix search (`startsWith`) requires iterating all n stored words and calling `word.startsWith(prefix)` on each — O(n × L). No structural support for prefix queries.

**Key insight:** Each string becomes a path of edges from root. An `isEnd` flag at the last node distinguishes a complete word ("app") from a word that's only a prefix of something longer ("ap" when only "apple" is stored). Walking L edges = O(L) lookup for any vocabulary size.

**Steps in plain English:**

1. **Insert:** Walk character by character. Where a child node doesn't exist, create one. After the last character, set `isEnd = true`.
2. **Search:** Walk character by character. If any character has no child, return false immediately. After the last character, return `node.isEnd` — must be a complete word, not just a prefix node.

```java
class Trie {
    private static class TrieNode {
        TrieNode[] children = new TrieNode[26];
        boolean isEnd = false;
    }

    private final TrieNode root = new TrieNode();

    public void insert(String word) {
        TrieNode node = root;
        for (char c : word.toCharArray()) {
            // c - 'a' maps 'a'→0, 'b'→1, ..., 'z'→25
            int idx = c - 'a';
            if (node.children[idx] == null) {
                node.children[idx] = new TrieNode();
            }
            node = node.children[idx];
        }
        // Step 1 — mark the terminal node as a complete word
        node.isEnd = true;
    }

    public boolean search(String word) {
        TrieNode node = root;
        for (char c : word.toCharArray()) {
            int idx = c - 'a';
            if (node.children[idx] == null) {
                // Step 2 — missing child = this word was never inserted
                return false;
            }
            node = node.children[idx];
        }
        // isEnd check: without it, search("ap") would return true when only "apple" is stored
        return node.isEnd;
    }
}
```

**Complexity (optimal):** O(L) per insert/search, O(N × L) total space — N words, L = average word length

**🏷️ Problems:** LC 208 (Implement Trie), LC 720 (Longest Word in Dictionary).

---

## 🧭 Pattern 2: Prefix Search (startsWith) ⭐

**What this solves:** Check whether any stored word begins with a given prefix. The answer requires no `isEnd` check — just reaching the end of the prefix is enough. Used in autocomplete and all "does this prefix exist?" queries.

**Recognition cues — reach for this when:**
- "Does any stored word start with this prefix?"
- "Autocomplete — return suggestions for a prefix"
- Problem combines exact lookup WITH prefix existence checks

**Brute force:** Store words in a list; for each prefix query iterate all words calling `word.startsWith(prefix)`. O(n × L) per query — unusable at scale (e.g., search-engine autocomplete).

**Key insight:** Walk the trie to the end of the prefix. If you reach there without a missing node, at least one stored word continues from (or ends at) that node. No `isEnd` check needed — any node reachable from root via the prefix characters confirms the prefix exists.

**Steps in plain English:**

1. **Walk** the prefix character by character through existing nodes.
2. **If any character's child is null**, return false — no word with this prefix was inserted.
3. **If you exhaust the prefix** without a missing node, return true.

```java
public boolean startsWith(String prefix) {
    TrieNode node = root;
    for (char c : prefix.toCharArray()) {
        int idx = c - 'a';
        if (node.children[idx] == null) {
            // Step 2 — no word in the trie starts with this prefix
            return false;
        }
        node = node.children[idx];
    }
    // Step 3 — reached end of prefix successfully
    // KEY DIFFERENCE from search(): we do NOT check node.isEnd here
    return true;
}
```

**Complexity (optimal):** O(L) time, O(1) extra space — just walking existing nodes, creating nothing

**🏷️ Problems:** LC 208 (Implement Trie — startsWith method), LC 1268 (Search Suggestions System).

---

## 🧭 Pattern 3: Wildcard Search (. matches any character)

**What this solves:** Search for words where `.` matches any single letter. You don't know which character to follow at a `.` node, so you must try all possibilities — this turns the iterative walk into a recursive DFS.

**Recognition cues — reach for this when:**
- "Design a data structure supporting search where `.` matches any letter"
- Pattern matching / regex-like queries over a stored dictionary
- Any search where some positions are "unknown"

**Brute force:** Store all words in a list; use `Pattern.compile` regex to match against each. O(n × L) per query with high constant factor from regex compilation.

**Key insight:** A normal character narrows the path to exactly one child. A `.` means we don't know which child — so we try ALL 26 children recursively. If ANY branch succeeds, the match is found. This is DFS with branching only at `.` characters.

**Steps in plain English:**

1. **Recursive helper** with current node and current position in the search word.
2. **Base case:** reached end of word → return `node.isEnd`.
3. **Known character:** follow the specific child (same as Pattern 1 search).
4. **`.` character:** loop over all 26 children; if any non-null child leads to a match, return true.

```java
public boolean search(String word) {
    // Step 1 — delegate to recursive DFS helper
    return dfs(root, word, 0);
}

private boolean dfs(TrieNode node, String word, int i) {
    // Step 2 — base case: consumed all characters
    if (i == word.length()) {
        return node.isEnd;
    }
    char c = word.charAt(i);
    if (c == '.') {
        // Step 4 — wildcard: try every non-null child
        for (TrieNode child : node.children) {
            if (child != null && dfs(child, word, i + 1)) {
                return true;
            }
        }
        return false;
    } else {
        // Step 3 — known character: follow the one specific child
        int idx = c - 'a';
        if (node.children[idx] == null) {
            return false;
        }
        return dfs(node.children[idx], word, i + 1);
    }
}
```

**Complexity (optimal):** O(L) per query for no-wildcard words; O(26^d × L) worst case where d = number of `.` characters — in practice much faster due to sparse tries

**🏷️ Problems:** LC 211 (Design Add and Search Words Data Structure).

---

## 🧭 Pattern 4: DFS + Trie (Word Search in Grid) ⭐

**What this solves:** Given a 2D character grid and a list of target words, find ALL words that can be formed by a connected path of adjacent cells (no cell reused per path). Checking each word individually with separate DFS passes is far too slow for large word lists.

**Recognition cues — reach for this when:**
- "Find all words from a list that appear in a character grid"
- "Backtracking on grid + multiple target words simultaneously"
- You'd normally DFS per word but the word list is large (30,000+ words)

**Brute force:** For each word, run a full DFS from every grid cell trying to spell that word. O(words × cells × 4^L) — with 30,000 words and a 12×12 grid this is completely impractical.

**Key insight:** Build a trie from ALL target words, then run ONE unified DFS from each cell, following the trie simultaneously. The trie shares exploration of common prefixes — if 1,000 words start with "cat", one DFS along "c→a→t" serves all 1,000 simultaneously. A null trie node is an instant prune — no target word starts with this path, stop exploring immediately.

**Steps in plain English:**

1. **Build trie** from all target words. Store the full word string at terminal nodes (field `word`) instead of just `isEnd` — avoids path reconstruction.
2. **DFS from every cell** in the grid, carrying the current trie node.
3. **At each cell:** if the trie has no child for this character → prune (return). If it does → advance both board position AND trie node.
4. **If `node.word != null`** → found a complete word. Add to results, set `node.word = null` to prevent duplicates.
5. **Mark cell visited** with `#`, recurse in 4 directions, then restore to original character.

```java
// Step 1 — TrieNode with 'word' field (replaces isEnd for this pattern)
private static class TrieNode {
    TrieNode[] children = new TrieNode[26];
    String word = null;  // non-null means a complete word ends here
}

public List<String> findWords(char[][] board, String[] words) {
    TrieNode trieRoot = new TrieNode();
    for (String w : words) {
        TrieNode node = trieRoot;
        for (char c : w.toCharArray()) {
            int idx = c - 'a';
            if (node.children[idx] == null) {
                node.children[idx] = new TrieNode();
            }
            node = node.children[idx];
        }
        // Store full word at terminal node — avoids path reconstruction
        node.word = w;
    }
    List<String> result = new ArrayList<>();
    // Step 2 — launch DFS from every cell
    for (int r = 0; r < board.length; r++) {
        for (int c = 0; c < board[0].length; c++) {
            dfs(board, r, c, trieRoot, result);
        }
    }
    return result;
}

private void dfs(char[][] board, int r, int c, TrieNode node, List<String> result) {
    if (r < 0 || r >= board.length || c < 0 || c >= board[0].length) {
        return;
    }
    char ch = board[r][c];
    if (ch == '#') {
        // Step 5 — cell already used in the current path
        return;
    }
    TrieNode next = node.children[ch - 'a'];
    if (next == null) {
        // Step 3 — prune: no target word continues with this character
        return;
    }
    if (next.word != null) {
        // Step 4 — complete word found
        result.add(next.word);
        // Null out to prevent finding the same word via a different path
        next.word = null;
    }
    // Step 5 — mark visited, explore 4 directions, restore
    board[r][c] = '#';
    dfs(board, r + 1, c, next, result);
    dfs(board, r - 1, c, next, result);
    dfs(board, r, c + 1, next, result);
    dfs(board, r, c - 1, next, result);
    board[r][c] = ch;
}
```

### 🎨 Visual — DFS + Trie Pruning

```
Grid (LC 212 actual test case):      Trie from words ["oath","eat","pea","rain"]:
  col→  0    1    2    3               root
  row 0 [o]  [a]  [a]  [n]             ├─ e → a → t  [word="eat"]
  row 1 [e]  [t]  [a]  [e]             ├─ o → a → t → h  [word="oath"]
  row 2 [i]  [h]  [k]  [r]             ├─ p → e → a  [word="pea"]
  row 3 [i]  [f]  [l]  [v]             └─ r → a → i → n  [word="rain"]

─────────────────────────────────────────────────────────
DFS from (0,0)='o':
  root has child 'o' → enter node(o), mark (0,0)='#'
  ├─ try (0,1)='a': node(o) has child 'a' → enter node(oa), mark (0,1)='#'
  │    ├─ try (1,1)='t': node(oa) has child 't' → enter node(oat), mark (1,1)='#'
  │    │    ├─ try (2,1)='h': node(oat) has child 'h'
  │    │    │    → node(oath).word = "oath"  ✓  ADD "oath", set word = null
  │    │    └─ try (1,2)='a': node(oat) has NO child 'a'  ✗ PRUNE
  │    └─ try (0,2)='a': node(oa) has NO child 'a' (only 't')  ✗ PRUNE
  └─ try (1,0)='e': node(o) has NO child 'e'  ✗ PRUNE
  Restore all cells.  Result so far: ["oath"]

─────────────────────────────────────────────────────────
DFS from (1,3)='e':
  root has child 'e' → enter node(e), mark (1,3)='#'
  ├─ try (0,3)='n': node(e) has NO child 'n'  ✗ PRUNE
  ├─ try (2,3)='r': node(e) has NO child 'r'  ✗ PRUNE
  └─ try (1,2)='a': node(e) has child 'a' → enter node(ea), mark (1,2)='#'
       └─ try (1,1)='t': node(ea) has child 't'
            → node(eat).word = "eat"  ✓  ADD "eat", set word = null
  Restore all cells.  Result so far: ["oath","eat"]

─────────────────────────────────────────────────────────
Why the trie wins:
  Without trie: 4 words × 16 start cells × up to 4^L DFS paths each
  With trie:    ONE DFS per start cell covers ALL words simultaneously;
                a null child is a hard stop — entire path subtree skipped

KEY INVARIANT:
   The grid cursor (r,c) and the trie pointer advance in lockstep, character
   by character. A null trie child means NO stored word can continue down
   this path — the full subtree is pruned without exploring it.
   Setting node.word = null after collection blocks duplicate results when
   the same word can be spelled via two different cell paths.
```

**Complexity (optimal):** O(M × 4 × 3^(L-1)) per starting cell, where M = total grid cells, L = max word length — trie pruning cuts the practical runtime drastically

**🏷️ Problems:** LC 212 (Word Search II), LC 79 (Word Search — single word, plain backtracking, no trie needed).

---

## 🧭 Pattern 5: Shortest Prefix Walk (Replace Words)

**What this solves:** Given a dictionary of root words and a sentence, replace each word in the sentence with the shortest dictionary root that is a prefix of it. If no root matches, keep the original word. The trie makes this O(L) per word — walk until you hit the first `isEnd` node and stop.

**Recognition cues — reach for this when:**
- "Replace each word with its shortest dictionary root"
- "Find the shortest prefix of an input word that exists in a dictionary"
- "Does any dictionary entry fully prefix this word?"

**Brute force:** For each sentence word, iterate all dictionary roots and check `word.startsWith(root)`; keep the shortest match. O(sentence_words × dict_size × root_length).

**Key insight:** Walk the trie character by character through the input word. The FIRST node you reach where `isEnd == true` IS the shortest matching root — return it immediately. You don't need to walk the full word; the trie's structure guarantees the first `isEnd` is the shortest match because shorter roots were inserted deeper first.

**Steps in plain English:**

1. **Build trie** from all dictionary root words, marking `isEnd` at each root's terminal node.
2. **For each sentence word**, walk the trie character by character.
3. **Stop immediately** at the first node with `isEnd == true` — that's the shortest matching root.
4. **If the trie path ends** without an `isEnd` hit, use the original word unchanged.

```java
public String replaceWords(List<String> dictionary, String sentence) {
    // Step 1 — build trie from dictionary roots
    TrieNode trieRoot = new TrieNode();
    for (String root : dictionary) {
        TrieNode node = trieRoot;
        for (char c : root.toCharArray()) {
            int idx = c - 'a';
            if (node.children[idx] == null) {
                node.children[idx] = new TrieNode();
            }
            node = node.children[idx];
        }
        node.isEnd = true;
    }
    String[] parts = sentence.split(" ");
    StringBuilder sb = new StringBuilder();
    for (int w = 0; w < parts.length; w++) {
        if (w > 0) {
            sb.append(' ');
        }
        // Step 2 — walk trie through the sentence word
        TrieNode node = trieRoot;
        boolean replaced = false;
        for (int i = 0; i < parts[w].length(); i++) {
            int idx = parts[w].charAt(i) - 'a';
            if (node.children[idx] == null) {
                // No root prefix exists for this word — use original
                break;
            }
            node = node.children[idx];
            if (node.isEnd) {
                // Step 3 — first isEnd hit = shortest matching root
                sb.append(parts[w], 0, i + 1);
                replaced = true;
                break;
            }
        }
        // Step 4 — no root matched, keep original word
        if (!replaced) {
            sb.append(parts[w]);
        }
    }
    return sb.toString();
}
```

**Complexity (optimal):** O(N + S) time — N = total characters in dictionary, S = total characters in sentence; O(N) space for trie

**🏷️ Problems:** LC 648 (Replace Words), LC 720 (Longest Word in Dictionary — find longest word where every prefix also exists in array).

---

## 🔬 Canonical Problem — LC 208: Implement Trie

> **Problem:** Design a Trie class with three operations: `insert(word)` adds a word, `search(word)` returns true only if the exact word was inserted, `startsWith(prefix)` returns true if any inserted word starts with the given prefix. Example: insert "apple" → search("apple")=true, search("app")=false, startsWith("app")=true.

### Step 1 — Read and identify triggers

"Implement a Trie" directly maps to **Patterns 1 + 2**. Three operations share one underlying node structure. The implementation IS the insight test here.

### Step 2 — Choose the template

Array-backed `TrieNode[26]` — problem specifies lowercase English letters only. The key design choice: ONE `TrieNode` class, THREE operations that all do "walk from root" but differ in what happens at the end.

### Step 3 — Adapt and code

**Steps in plain English:**

1. **TrieNode:** 26-slot children array + `isEnd` flag. That's it.
2. **insert:** walk + create nodes where gaps exist, mark last node `isEnd = true`.
3. **search:** walk existing nodes only, return `node.isEnd` at end. The `isEnd` check is what separates "app" from "apple."
4. **startsWith:** same walk as search, but return `true` at the end — no `isEnd` check.

```java
class Trie {
    private static class TrieNode {
        TrieNode[] children = new TrieNode[26];
        boolean isEnd = false;
    }

    private final TrieNode root = new TrieNode();

    public void insert(String word) {
        TrieNode node = root;
        for (char c : word.toCharArray()) {
            int idx = c - 'a';
            if (node.children[idx] == null) {
                node.children[idx] = new TrieNode();
            }
            node = node.children[idx];
        }
        node.isEnd = true;
    }

    public boolean search(String word) {
        TrieNode node = root;
        for (char c : word.toCharArray()) {
            int idx = c - 'a';
            if (node.children[idx] == null) {
                return false;
            }
            node = node.children[idx];
        }
        // The isEnd check: "apple" inserted ≠ search("app") returning true
        return node.isEnd;
    }

    public boolean startsWith(String prefix) {
        TrieNode node = root;
        for (char c : prefix.toCharArray()) {
            int idx = c - 'a';
            if (node.children[idx] == null) {
                return false;
            }
            node = node.children[idx];
        }
        // No isEnd check — reaching the prefix end is sufficient
        return true;
    }
}
```

### Step 4 — Verify with example

Insert `"apple"`: root→a→p→p→l→e (isEnd=true)

- `search("apple")` → walk 5 nodes → e.isEnd=true → **true** ✓
- `search("app")` → walk 3 nodes → p.isEnd=false → **false** ✓
- `startsWith("app")` → walk 3 nodes → reached prefix end → **true** ✓
- `startsWith("b")` → root.children['b'-'a']=null → **false** ✓

### Complexity
- **Time:** O(L) per operation — L = length of word or prefix
- **Space:** O(N × L × 26) — N words, each up to L characters, 26 pointers per node

---

## ⚡ Problem Bank — Key Twists

---

### LC 208: Implement Trie

> **Problem:** Build a Trie class with `insert(word)`, `search(word)`, and `startsWith(prefix)`. Example: insert "apple" → search("apple")=true, search("app")=false, startsWith("app")=true.

> **Brute force:** `HashSet<String>` for exact search O(1); iterate all entries for prefix search O(n×L).
> **Key insight:** Each character = one edge. `isEnd` flag distinguishes a complete word from a node that's only a prefix stepping stone.
> **Approach:** Patterns 1+2 — array-backed `TrieNode[26]`. All three methods share "walk from root"; the only difference is what you return at the end.

```java
// insert: walk + create, mark isEnd
// search: walk existing only, return node.isEnd
// startsWith: walk existing only, return true (no isEnd check)
TrieNode node = root;
for (char c : word.toCharArray()) {
    int idx = c - 'a';
    if (node.children[idx] == null) {
        node.children[idx] = new TrieNode();  // insert creates; search/startsWith returns false
    }
    node = node.children[idx];
}
```

**Complexity (optimal):** O(L) per operation, O(N × L) total space

---

### LC 211: Design Add and Search Words

> **Problem:** Build a `WordDictionary` with `addWord(word)` and `search(word)` where `.` matches any single character. Example: add "bad", "dad", "mad" → search(".ad")=true, search("b..")=true, search("pad")=false.

> **Brute force:** Store words in a list; apply `Pattern.compile` regex per search. O(n × L) per query.
> **Key insight:** `.` means "I don't know which child to follow" — try ALL 26 children. One known character → one path; one `.` → up to 26 branches. DFS handles the branching naturally.
> **Approach:** Pattern 3 — standard trie insert + recursive DFS only when `.` appears.

```java
private boolean dfs(TrieNode node, String word, int i) {
    if (i == word.length()) {
        return node.isEnd;
    }
    char c = word.charAt(i);
    if (c == '.') {
        // Try every non-null child — '.' is an any-character wildcard
        for (TrieNode child : node.children) {
            if (child != null && dfs(child, word, i + 1)) {
                return true;
            }
        }
        return false;
    }
    int idx = c - 'a';
    return node.children[idx] != null && dfs(node.children[idx], word, i + 1);
}
```

**Complexity (optimal):** O(L) for exact words; O(26^d × L) worst case for d wildcards

---

### LC 212: Word Search II

> **Problem:** Given an m×n character grid and a list of words, return all words formable by connecting adjacent (non-diagonal) cells without reusing a cell. Example: board=[["o","a","a","n"],["e","t","a","e"],["i","h","k","r"],["i","f","l","v"]], words=["oath","pea","eat","rain"] → ["eat","oath"].

> **Brute force:** For each word, run DFS from every grid cell trying to spell that word. O(words × cells × 4^L) — impractical for 30,000-word dictionaries.
> **Key insight:** One trie for all words + one DFS sweep. Shared prefixes share one path of exploration; a null trie child prunes the ENTIRE branch for ALL words — no wasted work.
> **Approach:** Pattern 4 — build trie with `word` field at terminal nodes (easier than reconstructing path). DFS carries current trie node; mark `#` for visited; null out `node.word` after finding to deduplicate.

```java
// Store full word at terminal node — avoids path reconstruction mid-DFS
node.word = w;

// In DFS: prune when trie child is null (no word continues this way)
TrieNode next = node.children[ch - 'a'];
if (next == null) return;

// When word found: add to results, null it out to prevent duplicates
if (next.word != null) {
    result.add(next.word);
    next.word = null;
}
```

**Complexity (optimal):** O(M × 4 × 3^(L-1)) where M = grid cells, L = max word length

---

### LC 648: Replace Words

> **Problem:** Given a dictionary of root words and a sentence, replace each word in the sentence with its shortest matching root (a root that is a prefix of the word). If no root matches, keep the original word. Example: dictionary=["cat","bat","rat"], sentence="the cattle was rattled by the battery" → "the cat was rat by the bat".

> **Brute force:** For each sentence word, check all dictionary roots with `startsWith`, keep shortest match. O(sentence × dict × root_len).
> **Key insight:** Walk trie through the input word; the FIRST `isEnd=true` you hit IS the shortest root. Stop immediately — don't finish the word.
> **Approach:** Pattern 5 — build trie from dictionary, walk each sentence word. Return substring up to first `isEnd` hit.

```java
for (int i = 0; i < word.length(); i++) {
    int idx = word.charAt(i) - 'a';
    if (node.children[idx] == null) break;   // no root prefix — exit, use original
    node = node.children[idx];
    if (node.isEnd) {
        // First isEnd = shortest root match. Stop here.
        sb.append(word, 0, i + 1);
        replaced = true;
        break;
    }
}
```

**Complexity (optimal):** O(N + S) time — N = total chars in dictionary, S = total chars in sentence

---

### LC 720: Longest Word in Dictionary

> **Problem:** Find the longest word in an array such that every prefix of that word is also in the array. If tie, return lexicographically smallest. Example: ["w","wo","wor","worl","world"] → "world".

> **Brute force:** Sort by length; for each word check all prefixes are in a HashSet. O(n × L²) with the nested startsWith check.
> **Key insight:** In a trie, "every prefix is also in the dictionary" means every node on the path to this word has `isEnd=true`. One trie walk checks all prefix conditions simultaneously.
> **Approach:** Build trie, then DFS; only follow children where `child.isEnd == true`. Track the deepest (longest) valid path found.

```java
// DFS: only explore children that are marked as complete words (isEnd=true)
// This guarantees the path to any found word has all its prefixes stored too
for (int i = 0; i < 26; i++) {
    TrieNode child = node.children[i];
    if (child != null && child.isEnd) {
        // Extend path: every step on this path is a valid prefix
        dfs(child, path + (char)('a' + i), result);
    }
}
// Update result: longer word wins; if tied, lexicographically smaller wins
if (path.length() > result[0].length()) {
    result[0] = path;
}
```

**Complexity (optimal):** O(N × L) time — N words, L = average word length

---

### LC 677: Map Sum Pairs

> **Problem:** Design a `MapSum` class: `insert(key, val)` stores key→val (overwrite allowed). `sum(prefix)` returns the sum of values for all keys starting with the given prefix. Example: insert("apple",3) → sum("ap")=3; insert("app",2) → sum("ap")=5.

> **Brute force:** `HashMap<String,Integer>` for insert; for sum iterate all entries checking `startsWith`. O(1) insert, O(n×L) sum.
> **Key insight:** Extend TrieNode with an `int val` field that stores the sum of all values in its subtree. On insert, add the delta (new value minus old value) to every node on the path. Sum then becomes O(L): just reach the prefix endpoint and read its accumulated value.
> **Approach:** Trie with `int val` per node. Store key→value in a separate HashMap to compute deltas on overwrite.

```java
class MapSum {
    private static class TrieNode {
        TrieNode[] children = new TrieNode[26];
        int val = 0;  // sum of all values in this node's subtree
    }
    private TrieNode root = new TrieNode();
    private Map<String, Integer> map = new HashMap<>();
    // 🔄 Fallback for map: use a separate HashMap to track existing key values

    public void insert(String key, int val) {
        // delta handles overwrite: if "apple" was 3 and now set to 5, add only +2
        int delta = val - map.getOrDefault(key, 0);
        // 🔄 Fallback: map.put(key, val) then recompute sums from scratch
        map.put(key, val);
        TrieNode node = root;
        for (char c : key.toCharArray()) {
            int idx = c - 'a';
            if (node.children[idx] == null) {
                node.children[idx] = new TrieNode();
            }
            node = node.children[idx];
            // Propagate delta to every node on the path — maintains subtree sums
            node.val += delta;
        }
    }

    public int sum(String prefix) {
        TrieNode node = root;
        for (char c : prefix.toCharArray()) {
            int idx = c - 'a';
            if (node.children[idx] == null) {
                return 0;
            }
            node = node.children[idx];
        }
        // The val at the prefix endpoint = sum of all values in this subtree
        return node.val;
    }
}
```

**Complexity (optimal):** O(L) per insert, O(L) per sum

---

### LC 1268: Search Suggestions System

> **Problem:** Given a list of products and a search word typed character by character, return for each prefix of `searchWord` up to 3 lexicographically smallest products that start with that prefix. Example: products=["mobile","mouse","moneypot","monitor","mousepad"], searchWord="mouse" → [["mobile","moneypot","monitor"],["mobile","moneypot","monitor"],["mouse","mousepad"],["mouse","mousepad"],["mouse","mousepad"]].

> **Brute force:** For each prefix, filter all products by `startsWith`, sort, take 3. O(P × L²) where P = products count.
> **Key insight:** Sort products first. For each prefix, binary search for the first product ≥ prefix, then take up to 3 that still match. O(P log P + L log P) total — faster than rebuilding per prefix.
> **Approach:** Sort + binary search is simpler than trie here. Or: build trie where each node stores up to 3 lexicographically smallest completions (insert-time pruning).

```java
Arrays.sort(products);
// 🔄 Fallback: List<String> list = Arrays.asList(products); Collections.sort(list);
List<List<String>> result = new ArrayList<>();
String prefix = "";
for (char c : searchWord.toCharArray()) {
    prefix += c;
    // Collections.binarySearch: finds first index where product >= prefix
    int lo = lowerBound(products, prefix);
    List<String> suggestions = new ArrayList<>();
    for (int j = lo; j < Math.min(lo + 3, products.length); j++) {
        if (products[j].startsWith(prefix)) {
            suggestions.add(products[j]);
        }
    }
    result.add(suggestions);
}
```

**Complexity (optimal):** O(P log P + L × log P) time with sort + binary search per prefix

---

## ⚠️ Interview Gotchas

### Edge cases interviewers probe
- **Empty string insert** — the for-loop doesn't execute; you immediately set `root.isEnd = true`. Usually valid but clarify whether empty string is a legal word.
- **Duplicate inserts** — inserting the same word twice just sets `isEnd = true` again. Harmless with array-backed nodes.
- **search vs startsWith confusion** — the most common LC 208 bug. `search("app")` MUST return false if only "apple" was inserted. The `isEnd` check IS the difference. Rehearse this explicitly.
- **Word Search II — same word via two paths** — without setting `node.word = null` after finding, the same word gets added twice to results. The null-out is mandatory.
- **Character set** — if the problem has uppercase letters, digits, or spaces, `c - 'a'` gives wrong indices or throws `ArrayIndexOutOfBoundsException`. Switch to `HashMap<Character, TrieNode>` and state why.
- **`new TrieNode[26]` initialization** — Java initializes this array with nulls automatically. You don't need `Arrays.fill`. But in a class definition, unlike primitives, `TrieNode[] children` defaults to `null`. Do `TrieNode[] children = new TrieNode[26];` in the field declaration.

### Follow-up questions to expect
- "What's the space complexity vs HashSet?" → Trie: O(alphabet_size × total_chars) pointers. HashSet: O(total_chars) for the strings themselves. Trie is worse for space but enables O(L) prefix queries that HashSet cannot do efficiently.
- "Can you delete a word from the trie?" → Walk to end node, set `isEnd = false`. Optionally clean up orphaned nodes (no children + not isEnd) recursively on the way back — needs a boolean return indicating "you can delete me."
- "Why not just sort the words and binary search?" → For a single prefix that's fine (like LC 1268). But for pattern matching (wildcards), grid search (many prefixes simultaneously), or streaming inserts, the trie's structural prefix sharing beats sorting.

### Complexity traps
- **`TrieNode[26]` memory:** Each node allocates 26 pointers even if it has only 1 child. For a trie storing 10,000 words of length 10, that's up to 100,000 × 26 × 8 bytes = ~20MB. For tiny alphabets this is fine; for large alphabets (Unicode), use HashMap.
- **Word Search II without `next.word = null`:** The same word reachable from cells (0,1) and (2,3) gets added twice. This causes wrong output that tests miss unless they sort and deduplicate.
- **Restoring `board[r][c]`:** Forgetting to restore after DFS backtrack permanently corrupts the grid for cells visited from other starting positions.
- **`c - 'a'` overflow for non-lowercase chars:** If problem says "letters" but test cases include uppercase or digits, this will throw. Always confirm character set with interviewer.

---

## 🧩 Speed Drill — 8 Minutes

**Part 1 — Pattern Recognition (2 minutes)**
For each description, name the pattern in under 5 seconds:

1. "Implement a dictionary with insert, search, and startsWith" → ___
2. "Find all words from a list that appear in a character grid" → ___
3. "Replace each word with its shortest dictionary root prefix" → ___
4. "Search for words where `.` matches any character" → ___
5. "Does any stored word start with the prefix `xyz`?" → ___

**Part 2 — Write From Memory (4 minutes)**
Without looking, write:
1. The `TrieNode` class for Patterns 1–3 (two fields minimum)
2. The complete `insert(String word)` method
3. The key difference between `search` and `startsWith` — one line of code differs

**Part 3 — Trace Through (2 minutes)**
Insert "cat" and "car" into a trie. Draw the resulting structure. Then answer:
- `search("cat")` → ?
- `search("ca")` → ?
- `startsWith("ca")` → ?

**Scoring:**
- Part 1: 5/5 → ready. Confused wildcard and DFS+Trie → re-read Patterns 3 and 4.
- Part 2: Wrote `TrieNode` with `children[26]` and `isEnd` + correct `insert` + identified `isEnd` as the search/startsWith difference → ready.
- Part 3: Correct tree with root→c→a→t[●] and root→c→a→r[●], answered true/false/true → ready. Wrong on `search("ca")=false` → re-read the isEnd explanation in Pattern 1.

---

## 🔗 Cross-References

- **Grid DFS / backtracking:** `../Interview/backtracking.md` — Pattern 4 here extends single-word grid backtracking; read backtracking first if Pattern 4 feels unclear
- **Trees fundamentals:** `../DeepDive/trees-fundamentals.md` — a trie is a tree; DFS on a trie uses the same pre-order traversal skeleton
- **Graphs grid problems:** `../Interview/graphs.md` — the 4-direction DFS template in Pattern 4 is identical to grid BFS/DFS in graphs
- **Strings:** `../Interview/strings.md` — string manipulation patterns that come before trie work; `StringBuilder` usage is shared

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| June 2026 | **File created.** Tries Interview Playbook — 5 patterns (Insert+Search, Prefix/startsWith, Wildcard DFS, DFS+Trie grid search, Shortest Prefix Walk), canonical walkthrough (LC 208), 7 problem bank entries with brute force + key insight + complexity. Added as FAANG gap fill after completing the 13-playbook format upgrade pass. |
