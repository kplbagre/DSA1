# Accounts Merge

> **LeetCode:** [721. Accounts Merge](https://leetcode.com/problems/accounts-merge/) — Medium
> **Pattern:** Format 7a — Invert-the-Map Derived Adjacency (see `DSA/DeepDive/graphs-fundamentals.md` § Format 7)
> **Uses:** HashMap inverted index, DFS on derived graph, TreeSet (sorted dedup), Union-Find

---

## 📌 Problem

Given a list of accounts where each account is `[name, email1, email2, ...]`, **merge all accounts that belong to the same person** (defined as: two accounts are the same person if they share at least one email address).

Return each merged person's account as `[name, sortedEmail1, sortedEmail2, ...]`. The same email will never appear under two different names.

### Examples

```
Input:
  [["John", "john@a.com", "john@b.com"],
   ["John", "john@b.com", "john@c.com"],
   ["Mary", "mary@a.com"]]

Output:
  [["John", "john@a.com", "john@b.com", "john@c.com"],
   ["Mary", "mary@a.com"]]
```

```
Input:
  [["Alice", "alice@x.com"],
   ["Alice", "alice@y.com"]]

Output:
  [["Alice", "alice@x.com"],
   ["Alice", "alice@y.com"]]
```

> Edge case: two accounts with the same name but no shared emails are DIFFERENT people — they stay separate.

### Constraints (typical)

- `1 ≤ accounts.length ≤ 1000`
- `2 ≤ accounts[i].length ≤ 10` (name + up to 9 emails)
- `1 ≤ accounts[i][j].length ≤ 30`
- Emails are lowercase English letters and `@`

---

## 🧠 Pattern Recognition

> **"Two entities are 'the same' if they share any element from a set — and you must merge groups transitively."**
>
> Whenever you see this structure in a problem, your first thought should be:
> 1. **Can I define an edge rule based on shared membership?** (Here: same email → same person)
> 2. **Is the merge transitive?** (Here: A shares email with B, B shares email with C → A/B/C all same person)
> 3. **If yes → this is a connected-components problem on a DERIVED graph — build the graph with Format 7a, then run DFS/Union-Find.**

The naive approach (compare every pair of accounts) hits O(N²×E). The correct approach **inverts the data** first: instead of "which accounts share an email?", ask "which accounts does each email appear in?" — this cuts the pairwise comparison entirely. Note: if many accounts share the same email, building all pairwise edges in DFS still hits O(bucket²) per email; Union-Find (Approach 3) avoids this by unioning with only one representative per email, keeping total work at O(N×E×α(N)).

This is the **Format 7a — Invert-the-Map** pattern, solved via **Union-Find** (a data structure that groups nodes into components using a parent-pointer tree — also called Disjoint Set Union / DSU — supporting near-constant union and find operations with path compression) or DFS. See **`DSA/DeepDive/graphs-fundamentals.md` § Format 7a** for the general framework.

---

## ❌ Approach 1: Pairwise Comparison (Brute Force)

> **The instinctive first solution** — compare every pair of accounts; merge if they share any email.

### Idea

1. For each account `i`, check every other account `j` for any common email.
2. If they share one, merge `j`'s emails into `i` and remove `j`.
3. Repeat until no more merges happen (because merging creates new sharing possibilities).

### Idea (structural sketch only — not worth implementing)

1. For each pair of accounts (i, j), check if their email sets intersect.
2. If yes, merge j's emails into i's email set, mark j as merged.
3. Restart from the beginning — merging can create new intersections with accounts already passed.
4. Repeat until a full pass produces no new merges.

```java
// Sketch — correct for small N, not practical at scale
Set<Integer> mergedInto = new HashSet<>();
for (int i = 0; i < n; i++) {
    if (mergedInto.contains(i)) {
        continue;
    }
    Set<String> emailsI = new HashSet<>(accounts.get(i).subList(1, accounts.get(i).size()));
    for (int j = i + 1; j < n; j++) {
        for (String email : accounts.get(j).subList(1, accounts.get(j).size())) {
            if (emailsI.contains(email)) {
                // merge j into i — but now i has more emails: must re-scan ALL j' > i
                emailsI.addAll(accounts.get(j).subList(1, accounts.get(j).size()));
                mergedInto.add(j);
                break;
            }
        }
    }
}
// ↑ This misses transitive merges: if A─B and B─C were separate passes,
//   A never sees C unless we restart. Correct fix = fixed-point loop → O(N³×E)
```

### Why this fails

- Each "restart until stable" pass is O(N²×E). In the worst case you need O(N) restarts → O(N³×E) total.
- Correct implementation requires careful removal or tombstoning — easy to introduce bugs.
- Interviewers use this problem specifically to filter candidates who go down this path.

### Complexity

| | |
| --- | --- |
| Time | **O(N³ × E²)** — worst case with repeated re-scans |
| Space | O(N × E) |

> **Why brute fails:** the problem is fundamentally about transitive closure. Pairwise comparison discovers direct edges but not transitivity — you'd need to re-run until a fixed point, making it polynomial on N.

---

## ✅ Approach 2: Invert-the-Map + DFS

> **The standard interview answer** — derive the graph in O(N×E), then find connected components with DFS.

### Idea

1. Invert the data: build `email → [account indices]`.
2. For each email that appears in 2+ accounts, connect those accounts as undirected graph edges.
3. DFS from each unvisited account to collect one component; gather all emails in the component, sort them, prepend name.

### 🎨 Visual — Build + Traverse

```
Input accounts (indices):
  0: ["John", "john@a.com", "john@b.com"]
  1: ["John", "john@b.com", "john@c.com"]
  2: ["Mary", "mary@a.com"]

Phase 1 — Invert: email → account indices
  john@a.com → [0]
  john@b.com → [0, 1]   ← shared by 0 and 1
  john@c.com → [1]
  mary@a.com → [2]

Phase 2 — Build adjacency list (shared emails → edges)
  john@b.com has [0, 1] → edge 0─1
  adj: { 0→{1},  1→{0},  2→{} }

Phase 3 — DFS to find components
  Start at 0 (unvisited):
    collect john@a.com, john@b.com
    visit neighbor 1:
      collect john@b.com, john@c.com   (TreeSet deduplicates)
  component emailSet: {john@a.com, john@b.com, john@c.com}
  name = accounts[0][0] = "John"
  → ["John", "john@a.com", "john@b.com", "john@c.com"]

  Skip 1 (already visited).

  Start at 2 (unvisited):
    collect mary@a.com, no neighbors
  → ["Mary", "mary@a.com"]

KEY INVARIANT:
  Every account index has exactly one DFS start — the outer loop.
  Every component is collected in one DFS traversal.
  TreeSet gives sorted + deduplicated emails for free.
```

### Code

**Steps in plain English:**

1. **Invert:** for each (account index, email) pair, add account index to `emailToAccounts[email]`.
2. **Build adjacency list:** for each email shared by 2+ accounts, connect those account indices as undirected edges.
3. **DFS:** for each unvisited account index, collect all reachable accounts' emails into a `TreeSet` (auto-sorted, deduped). Prepend the name.

```java
public List<List<String>> accountsMerge(List<List<String>> accounts) {
    // Step 1 — invert: email → all account indices that contain it
    Map<String, List<Integer>> emailToAccounts = new HashMap<>();
    for (int i = 0; i < accounts.size(); i++) {
        List<String> account = accounts.get(i);
        for (int j = 1; j < account.size(); j++) {
            String email = account.get(j);
            emailToAccounts.computeIfAbsent(email, k -> new ArrayList<>()).add(i);
        }
    }

    // Step 2 — build adjacency list: account index → connected account indices
    Map<Integer, Set<Integer>> adj = new HashMap<>();
    for (List<Integer> accountIndices : emailToAccounts.values()) {
        for (int i = 0; i < accountIndices.size(); i++) {
            for (int j = i + 1; j < accountIndices.size(); j++) {
                int a = accountIndices.get(i);
                int b = accountIndices.get(j);
                adj.computeIfAbsent(a, k -> new HashSet<>()).add(b);
                adj.computeIfAbsent(b, k -> new HashSet<>()).add(a);
            }
        }
    }

    // Step 3 — DFS: one start per component, collect all emails into TreeSet (sorted + deduped)
    Set<Integer> visited = new HashSet<>();
    List<List<String>> result = new ArrayList<>();
    for (int i = 0; i < accounts.size(); i++) {
        if (visited.contains(i)) {
            continue;
        }
        visited.add(i);
        Set<String> emailSet = new TreeSet<>();
        dfs(i, accounts, adj, visited, emailSet);
        List<String> merged = new ArrayList<>(emailSet);
        // name is index 0 in the original account list
        merged.add(0, accounts.get(i).get(0));
        result.add(merged);
    }
    return result;
}

private void dfs(int idx, List<List<String>> accounts,
                 Map<Integer, Set<Integer>> adj,
                 Set<Integer> visited, Set<String> emailSet) {
    // Collect all emails from this account
    List<String> account = accounts.get(idx);
    for (int j = 1; j < account.size(); j++) {
        emailSet.add(account.get(j));
    }
    // Visit all connected accounts
    for (int neighbor : adj.getOrDefault(idx, Collections.emptySet())) {
        if (!visited.contains(neighbor)) {
            visited.add(neighbor);
            dfs(neighbor, accounts, adj, visited, emailSet);
        }
    }
}
```

### Complexity

| | |
| --- | --- |
| Time | **O(N×E×log(N×E))** — building graph O(N×E), sorting emails O(E·total × log) via TreeSet |
| Space | O(N×E) — inverted index + adjacency list + visited set |

---

## 🚀 Approach 3: Invert-the-Map + Union-Find

> **The optimal (streaming-friendly) solution** — same O complexity but lower constant; handles accounts arriving one at a time.

### Idea

1. Same invert step — `email → first account index that claimed it`.
2. For each subsequent account that contains the same email: `union(currentAccount, firstAccount)`.
3. After all unions, group accounts by root. Collect emails per root, sort, prepend name.

### Code

**Steps in plain English:**

1. **Initialize DSU** with one node per account.
2. **Invert + union in one pass:** for each email, if it was seen before, union the current account with the first account that claimed it. Otherwise record current account as the claimant.
3. **Group by root:** for each account, find its root and add all its emails to the root's `TreeSet`.
4. **Build result:** for each root account, prepend its name to the sorted email list.

```java
public List<List<String>> accountsMerge(List<List<String>> accounts) {
    int n = accounts.size();
    // Step 1 — initialize DSU: each account is its own root
    int[] parent = new int[n];
    for (int i = 0; i < n; i++) {
        parent[i] = i;
    }

    // Step 2 — invert + union: email → first account index to claim it
    Map<String, Integer> emailToFirstAccount = new HashMap<>();
    for (int i = 0; i < n; i++) {
        List<String> account = accounts.get(i);
        for (int j = 1; j < account.size(); j++) {
            String email = account.get(j);
            if (emailToFirstAccount.containsKey(email)) {
                union(parent, i, emailToFirstAccount.get(email));
            } else {
                emailToFirstAccount.put(email, i);
            }
        }
    }

    // Step 3 — group accounts by root; collect emails per root into TreeSet
    Map<Integer, Set<String>> rootToEmails = new HashMap<>();
    for (int i = 0; i < n; i++) {
        int root = find(parent, i);
        List<String> account = accounts.get(i);
        Set<String> emails = rootToEmails.computeIfAbsent(root, k -> new TreeSet<>());
        for (int j = 1; j < account.size(); j++) {
            emails.add(account.get(j));
        }
    }

    // Step 4 — build result: prepend name (from root account)
    List<List<String>> result = new ArrayList<>();
    for (Map.Entry<Integer, Set<String>> entry : rootToEmails.entrySet()) {
        int root = entry.getKey();
        List<String> merged = new ArrayList<>(entry.getValue());
        merged.add(0, accounts.get(root).get(0));
        result.add(merged);
    }
    return result;
}

private int find(int[] parent, int x) {
    if (parent[x] != x) {
        // Path compression: flatten the tree on every lookup
        parent[x] = find(parent, parent[x]);
    }
    return parent[x];
}

private void union(int[] parent, int a, int b) {
    int ra = find(parent, a);
    int rb = find(parent, b);
    if (ra != rb) {
        parent[ra] = rb;
    }
}
```

### Complexity

| | |
| --- | --- |
| Time | **O(N×E×α(N))** — α is the inverse Ackermann function (effectively O(1)); plus O(E log E) for sorting → dominated by sorting |
| Space | O(N×E) |

---

## 📊 Approach Comparison

| Approach | Time | Space | Notes |
| --- | --- | --- | --- |
| 1. Pairwise comparison | **O(N³×E²)** | O(N×E) | Never implement this — infinite loop risk, still fails at scale |
| 2. Invert + DFS | **O(N²×E + E log E)** worst case | O(N×E) | One shared email with all N accounts → O(N²) edge pairs built; log E from TreeSet sorting |
| 3. Invert + Union-Find | **O(N×E×α(N) + E log E)** | O(N×E) | Near-linear regardless of bucket size; only unions with one representative per email; log E still from TreeSet |

> **Interview tip:** Code Approach 3 (Union-Find) if you're comfortable with DSU — it's strictly better asymptotically and demonstrates a more sophisticated pattern. Code Approach 2 (DFS) if you want simpler code that's easier to explain; mention "Union-Find would give true near-linear time regardless of email fan-out." Both are acceptable — never code Approach 1.

---

## 🔁 Variations & Follow-ups

### **1. What if the input is the UserConnections problem instead of accounts/emails?**

Same pattern, different surface framing:
- Input: `Map<String, List<String>> userToAttrs` instead of `List<List<String>> accounts`
- "Same person" rule → "connected users" rule
- Adjacency list builds on `String` user IDs instead of `Integer` account indices
- No sorting step needed (you return components, not merged sorted lists)

Full worked solution: `DSA/Practice/UserConnections.java`.

### **2. What if edges have weights (shared-count matters) and you filter by a threshold?**

Change `Map<Integer, Set<Integer>>` → `Map<Integer, Map<Integer, Integer>>`. Replace `.add(b)` with `.merge(b, 1, Integer::sum)`. At traversal time, skip edges where `weight < threshold`. Build time is identical; threshold filtering happens during BFS/DFS, not during graph construction (so the same graph works for any threshold).

### **3. What if you need to COUNT merged groups, not list them?**

Don't collect emails — just count how many times the outer DFS loop starts a new traversal. One-liner change: replace the email-collection DFS with a plain visited-marking DFS and increment a counter.

### **4. What if emails arrive one account at a time (streaming input)?**

Use Union-Find (Approach 3). DFS requires the full graph to be built before traversal. Union-Find can process each account as it arrives: for each email in the new account, check `emailToFirstAccount` — if seen, union; if not, record. This is the key reason to know both approaches.

### **5. What if an email appears under two different names (data error)?**

The problem statement guarantees this won't happen. In a real system, this signals a data integrity issue. The algorithm itself would still run (the email would union the two name-accounts), but the name chosen for the merged output would be arbitrary (whichever account index was the DSU root). In practice, you'd surface this as a conflict requiring manual review.

---

## 🎯 Key Takeaways

1. **"Shared membership" → invert the map first.** Any time two entities are connected because they share an element from a set, invert the data (`element → entities`) and connect the bucket. Never compare pairs directly — that's O(N²) and often wrong for transitivity.
2. **Derive the graph, then run the standard algorithm.** Accounts Merge = Format 7a build + standard DFS connected components. Recognizing the two-phase split (build graph / run algorithm) is more important than memorizing either step.
3. **TreeSet gives you sorted + deduplicated output for free.** Whenever you need a sorted, unique collection of strings, `new TreeSet<>()` is one line, not a sort + distinct step.
4. **DFS vs Union-Find: static input → DFS; streaming input → Union-Find.** Same time complexity, but Union-Find processes incrementally. Know when each applies.
5. **The isolation trap:** accounts with no shared emails are NOT merged — but they still appear in the output. The outer `for (int i = 0; i < accounts.size(); i++)` loop handles this automatically (DFS from an isolated account collects only its own emails).

---

## 🔗 Related Notes & Problems

### Notes referenced

- **`DSA/DeepDive/graphs-fundamentals.md` → § Format 7a** — general framework for invert-the-map adjacency construction
- **`DSA/DeepDive/graphs-fundamentals.md` → § Connected Components** — the DFS outer-loop pattern used in Approach 2
- **`DSA/Practice/UserConnections.java`** — worked coding exercise using Format 7a on a `Map<String, List<String>>` input

### Similar problems (same Format 7a pattern)

- **Largest Component Size by Common Factor** (LC 952) — integers connected if they share a common factor > 1; same invert (factor → integers) + Union-Find on shared-factor buckets; Format 7a exactly
- **Number of Provinces** (LC 547) — connected components but Format 5 (adjacency matrix); same DFS outer-loop
- **Redundant Connection** (LC 684) — Union-Find detecting a cycle; same DSU code, different question asked

### Adjacent problems (related but different format)

- **Similar String Groups** (LC 839) — Format 7b (condition-based); edge = one swap makes strings equal; O(N²) pair check unavoidable because no shared-set structure exists
- **Alien Dictionary** (LC 269) — Format 7c (sequence ordering); edge = directed character ordering rule; then topological sort
- **Min Cost to Connect All Points** (LC 1584) — Format 7d (spatial); edge = Manhattan distance; then Prim's / Kruskal's MST

---

## 🧪 Quick Self-Test

Without looking, can you:

- [ ] Name the two phases of this problem (hint: build ___ then run ___)?
- [ ] Write the inverted-index build step (email → account indices) from scratch in 2 minutes?
- [ ] Explain why `Set<Integer>` neighbors deduplicate edges automatically (when would two accounts be connected by more than one shared email)?
- [ ] Write `find()` with path compression in one recursive function?
- [ ] Name the one structural difference between UserConnections and Accounts Merge (hint: input type + what you return)?

If yes to all → you've internalized the **Format 7a Invert-the-Map** pattern. ✅

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| July 2026 | **File created.** Pattern note for LC 721, anchoring the Format 7a (Invert-the-Map) derived adjacency pattern. Triggered by working through the UserConnections system-design problem and identifying the gap in graphs-fundamentals.md. |
