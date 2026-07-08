# upgrade.md — Note Quality Upgrade Standard

> **Purpose:** A reusable checklist for scanning any deep-dive pattern note and identifying exactly what's missing. Use this before reviewing or upgrading any file in `DSA/DeepDive/`, `LLD/`, etc.
>
> Apply the checklist per pattern-section and per walkthrough. Flag issues, then fix in order of priority.

---

## 🎯 The Five Upgrade Types

Every pattern section or worked walkthrough can be missing one or more of these four things.

---

### Upgrade Type 1 — Missing Motivation ("Why does this beat brute force?")

**The problem:** The pattern section says WHAT the technique is and WHEN to use it, but never explains WHY it is O(n) instead of O(n²) or O(n³). The reader has no mental model of what redundant work is being skipped.

**What good looks like:**
```
Brute force: try every (left, right) pair = O(n²) windows.
Sliding window works because the constraint is MONOTONE:
  if [left..right] is invalid, then [left-1..right] is EVEN MORE invalid.
  No point moving left further left for this right.
  So left never resets — each element added once, removed at most once = O(n).
```

**The one question to ask:** *"If I read only this section, do I know why O(n) is possible — not just that it is?"*

**Where this typically appears in deep dives:**
- Template / pattern sections (after trigger phrase, before English steps)
- Any place where "just use this template" is stated without explaining the property that makes it valid

---

### Upgrade Type 2 — Missing Running Trace ("Show me the pointers moving")

**The problem:** Pattern sections jump from trigger phrase → English steps → code. There is no concrete example of the algorithm executing step by step. The reader cannot "see" the technique.

**What good looks like:**
```
Input: [1, 1, 0, 1, 1, 0, 1], K=1

right=0: add 1. zeros=0. window=[1].         len=1.  best=1
right=1: add 1. zeros=0. window=[1,1].       len=2.  best=2
right=2: add 0. zeros=1. window=[1,1,0].     len=3.  best=3
right=3: add 1. zeros=1. window=[1,1,0,1].   len=4.  best=4
right=4: add 1. zeros=1. window=[1,1,0,1,1]. len=5.  best=5  ← new best
right=5: add 0. zeros=2 > 1. SHRINK:
  remove [0]=1. zeros still 2. left=1.
  remove [1]=1. zeros still 2. left=2.
  remove [2]=0. zeros=1.      left=3. Window=[1,1,0], len=3.
right=6: add 1. zeros=1. window=[1,1,0,1].   len=4.  best stays 5.

Answer: 5
```

**The one question to ask:** *"Can I trace every step — what goes into the window, what comes out, what the pointers are — without reading the code?"*

**Format rules for running traces:**
- One `right=N:` line per outer loop iteration
- Show the SHRINK steps inline when they happen
- Mark `← new best` when the answer updates
- Keep the input small (6-8 elements) — enough to show at least one shrink cycle
- End with `Answer: X` so the correctness is verifiable

---

### Upgrade Type 3 — Unexplained Algorithmic Trick (the "invariant is missing")

**The problem:** A special optimization — like `slide-not-shrink`, `maxFreqEver`, `formed counter`, or `atMost(K) − atMost(K−1)` — is stated and implemented, but the INVARIANT it maintains is never articulated. The reader has to take it on faith.

**What good looks like (for slide-not-shrink):**
```
INVARIANT: the window size is MONOTONICALLY NON-DECREASING.
It either grows (when right expands and window stays valid)
or stays the same (when right expands but window is invalid — slide one step).
It NEVER shrinks.

WHY: we're searching for the LONGEST window. Once we've found a window of
size X, any window smaller than X is irrelevant — we've already beaten it.
When the window is invalid, we don't need to find a SHORTER valid window.
We need to find a LONGER one. So we just shift the window right (slide)
and wait for a chance to grow.

CONSEQUENCE: at the end, right - left + 1 equals the length of the longest
valid window seen, because the window size tracks the high-water mark.
```

**The one question to ask:** *"If I were explaining this trick to a junior dev in 30 seconds, could I state the property it relies on WITHOUT showing code?"*

**Known tricks that need explicit invariant statements:**
| Trick | File | Invariant to state |
|---|---|---|
| `slide-not-shrink` + `maxFreqEver` | two-pointers-sliding-window-fundamentals.md | Window size is monotonically non-decreasing; only grow or shift, never shrink |
| `formed` counter (LC 76) | two-pointers-sliding-window-fundamentals.md | formed == required is the single O(1) validity check; avoids map comparison |
| `atMost(K) − atMost(K−1)` | two-pointers-sliding-window-fundamentals.md | "exactly K" is not monotone; "at most K" is — the subtraction isolates exactly-K |
| BST bounds tightening | trees-fundamentals.md | Every node narrows the valid range for its subtree |
| DSU path compression | graphs-fundamentals.md | After find(), every node on the path points directly to root |

---

### Upgrade Type 4 — Self-Correction Artifact in Visual

**The problem:** A visual was written, found to be wrong mid-draft, corrected inline, but the self-correction prose was never removed. Under interview-prep pressure, re-reading "Hmm, actually..." in a diagram is deeply confusing.

**What bad looks like:**
```
right=4:  window=[1,2,1,2,3], distinct=3 → SHRINK
          count += 2   (total=12)

Hmm, actually from the existing trace: atMost(2)=13. Let me recheck:
After removing 1 (left=0), window=[2,1,2,3] distinct=3, still>2.
Actually the existing trace shows atMost(2)=13. The key: at right=3...
```

**Fix:** Delete all "Hmm", "Let me recheck", "Actually..." lines. Recompute the correct trace and write it cleanly. The final file shows only correct output.

**How to find these:** `grep -n "Hmm\|actually\|Let me recheck\|wait\.\.\." <file>`

---

### Upgrade Type 5 — Missing Method Fallbacks ("What do I write if I forget this method?")

**The problem:** The doc teaches patterns and habits using concise Java API methods (`merge`, `computeIfAbsent`, PQ comparators). Under interview pressure, a reader may know the logic but blank on the exact method name or lambda syntax. No fallback is shown, so they are stuck.

**What good looks like:**

```java
// Concise:
freq.merge(word, 1, Integer::sum);

// 🔄 Fallback — always works:
freq.put(word, freq.getOrDefault(word, 0) + 1);
```

**The one question to ask:** *"If I forget the shorthand, is there a plain `if-else` equivalent shown that I can write from memory in 10 seconds?"*

**Format rules:**
- Add a `### 🔄 Method Fallbacks — When You Forget the Shorthand` subsection as the **last subsection** inside the Style Habits section (before the next `##` section)
- Each method entry: bold header naming the method + one-line explanation → code block with `// What it does:` comments followed by `// 🔄 Fallback — always works:` and the plain equivalent
- Cover all methods used in that doc's pattern code (don't add methods the doc never uses)
- Standard set to check for: `merge`, `computeIfAbsent`, `getOrDefault`, `entrySet`, `!set.add(x)`, comparator lambdas (if the doc uses heaps/sorting)

**Where this applies:**
- Any deep-dive that teaches API shortcuts without showing the verbose fallback
- Cross-check with the corresponding `Interview/Playbooks/` file — if the playbook has a fallbacks section and the deep-dive doesn't, the deep-dive needs one

---

## 📋 Per-Section Checklist

Apply this to every **Pattern section** and every **Worked Walkthrough** in a deep-dive file.

### For a Pattern Section

- [ ] **Type 1 (Motivation):** Does the section state why brute force is O(n²/n³) AND what property makes O(n) possible? If not → add "Why brute force fails" paragraph before English steps.
- [ ] **Type 2 (Trace):** Does the section include a step-by-step running trace with 6-8 concrete values showing every pointer move? If not → add `### 🎨 Visual — Running trace` block.
- [ ] **Type 3 (Invariant):** If the pattern has a special trick (see table above), is the invariant stated in plain English BEFORE the code? If not → add invariant callout.
- [ ] **Type 4 (Clean visual):** Does any visual contain self-correction prose? If yes → rewrite clean.

### For a Deep-Dive File (whole-file audit)

- [ ] **Type 5 (Fallbacks):** Does the Style Habits section have a `### 🔄 Method Fallbacks` subsection with `// 🔄 Fallback` plain-code equivalents for every shorthand method used in the doc? If not → add it as the last subsection before the next `##`.
- [ ] **Cross-check:** Does the corresponding `Interview/Playbooks/` file have a fallbacks section that this deep-dive is missing?

### For a Worked Walkthrough

- [ ] **Intuition bridge exists:** Is there a "what cracks it open" section before the steps?
- [ ] **Type 3 (Invariant):** If the walkthrough introduces a trick, does it have an explicit invariant statement + running trace BEFORE the code?
- [ ] **Type 4 (Clean visual):** Grep for artifact prose.
- [ ] **Self-contained:** Can the walkthrough be solved ONLY from the notes (without needing the pattern section to fill in gaps)?

---

## 🗂️ Known Gaps by File (as of June 2026)

### `DSA/DeepDive/two-pointers-sliding-window-fundamentals.md`

| Location | Gap type | Status |
|---|---|---|
| Pattern 1 — Fixed Window | Type 1 (motivation missing) | ✅ done July 2026 |
| Pattern 2 — Longest Valid | Type 1 (motivation missing) + Type 2 (no trace) | ✅ done July 2026 |
| Pattern 3 — Shortest Valid | Type 1 (motivation missing) + Type 2 (no trace) | ✅ done July 2026 |
| Pattern 4 — Exactly K | Type 1 (motivation missing) + Type 2 (no trace) | ✅ done July 2026 |
| WW-6 — LC 424 | Type 3 (slide-not-shrink invariant missing) + Type 2 (no trace) | ✅ done July 2026 |
| WW-9 — LC 992 visual | Type 4 (self-correction artifact in visual) | ✅ done July 2026 |

### `DSA/DeepDive/hashmaps-fundamentals.md`

| Location | Gap type | Status |
|---|---|---|
| Style Habits section | Type 5 (method fallbacks missing) | ✅ done July 2026 |
| Pattern 1 — Frequency Map | Type 2 (no trace) | ✅ done July 2026 |
| Pattern 2 — Complement Lookup | Type 2 (no trace) | ✅ done July 2026 |
| Pattern 3 — Canonical Form | Type 2 (no trace) | ✅ done July 2026 |
| Pattern 4 — Prefix Sum | Type 2 (no trace) + Type 3 (KEY INVARIANT missing) | ✅ done July 2026 |
| Pattern 5 — Two-Pass for Order | Deferred stub (Hierholzer's needs graphs-fundamentals.md first) | ✅ deferred note added July 2026 |
| Pattern 6 — Custom Grouping | Steps + template code + trace missing | ✅ done July 2026 |
| WW-1 — LC 1 Two Sum | Type 2 (no trace) | ✅ done July 2026 |
| WW-2 — LC 242 Valid Anagram | Type 2 (no trace) | ✅ done July 2026 |
| WW-3 — LC 49 Group Anagrams | Type 2 (no trace) | ✅ done July 2026 |
| WW-4 — LC 347 Top K Frequent | Type 2 (no trace) + Type 3 (bucket-sort invariant missing) | ✅ done July 2026 |
| WW-5 — LC 560 Subarray Sum | Type 2 (no trace) | ✅ done July 2026 |
| WW-6 — LC 454 4Sum II | Type 2 (no trace) | ✅ done July 2026 |
| WW-7 — LC 380 Insert Delete GetRandom | Type 2 (no trace) + Type 3 (swap-with-last invariant missing) | ✅ done July 2026 |
| WW-8 — LC 146 LRU Cache | Type 2 (no trace) + Type 3 (DLL+map invariant missing) | ✅ done July 2026 |

### `DSA/DeepDive/binary-search-fundamentals.md`

> Created July 2026 from scratch following all current standards. No inherited gaps. Register here for future audits.

| Location | Gap type | Status |
|---|---|---|
| All 5 patterns | Types 1–5 applied at creation | ✅ clean at creation |
| Style Habits — Method Fallbacks | Type 5 applied at creation (ceiling division fallback) | ✅ clean at creation |
| All 9 walkthroughs | 5-part format applied at creation | ✅ clean at creation |

---

### `DSA/DeepDive/graphs-fundamentals.md`

> Audited in parallel session. Three SP visuals added (BFS-vs-Dijkstra, Bellman-Ford trace, Floyd-Warshall relay). No known gaps as of June 2026.

---

## 🔄 Upgrade Workflow

1. Open `upgrade.md` — identify the file and section to upgrade
2. Read the target section
3. Apply only the gaps flagged in the "Known Gaps" table
4. After applying ONE section, get review — then propagate the style to remaining sections
5. Mark the row in "Known Gaps" as ✅ done

---

## 🔄 Changelog

| Date | Change |
|---|---|
| July 2026 | Created. Captures 4 upgrade types identified from two-pointers review. Known gaps table seeded with two-pointers and graphs files. |
| July 2026 | **Added Upgrade Type 5 — Missing Method Fallbacks.** Triggered by gap found in `hashmaps-fundamentals.md` vs `arrays-and-hashing.md` (playbook had fallback section, deep-dive did not). Added Type 5 to the per-section checklist as a whole-file audit item. Added hashmaps to Known Gaps table (✅ done). |
| July 2026 | **Full hashmaps-fundamentals.md upgrade (Types 2, 3).** Applied running traces to all 6 patterns and all 8 walkthroughs. KEY INVARIANT callouts added for Pattern 4 (prefix count), WW-4 (bucket sort), WW-7 (swap-with-last), WW-8 (LRU DLL). Pattern 6 completed (steps + template code + trace). Pattern 5 marked deferred. Known Gaps table updated with 14 new rows, all ✅ done. |
| July 2026 | **Registered binary-search-fundamentals.md.** File created from scratch applying all 5 upgrade types at creation time — no inherited gaps. Added to Known Gaps table as clean baseline for future audits. |
