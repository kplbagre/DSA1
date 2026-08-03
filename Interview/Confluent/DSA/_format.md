# Confluent DSA Battle Pack — Format Standard

> **Read this file before writing or editing any problem file in this folder.**

---

## Problem Format (every problem follows this exactly)

```
### LC XXX — Problem Name  (or "Custom — Problem Name" if not on LeetCode)

**🎤 How It's Asked:**
Verbatim-style interview framing. How would the interviewer phrase this?
Include alternate framings if the same problem shows up disguised.

**Discussion — How to arrive at the solution:**
Walk through the THINKING process, not the answer.
What do you notice? What's the naive instinct? What breaks it?
What data structure or technique does this remind you of?
What's the subproblem structure?

**Brute Force:**
- Approach (2-4 lines)
- Code (if worth showing — skip for trivially obvious brute forces)
- Time: O(?) — WHY: derive it, don't just state it
- Space: O(?) — WHY: derive it, don't just state it

**Key Insight:**
Multiple sentences. This is a THINKING FRAMEWORK — teach Kapil how to
arrive at the optimal approach, not just what the approach is.
"When you see X, think Y. The reason this works is Z."

**Optimal Solution:**

**Steps in plain English:**
Numbered list of what the code will do, BEFORE the code.
Each step = one logical action (not one line of code).
Purpose: Kapil reads the steps, understands the approach,
THEN reads the code with step-matching comments.

- Code (full working Java, follows code hygiene rules below)
  - Each major block has a comment like "// Step 3 — description"
    matching the numbered steps above
- Time: O(?) — WHY: derive from the code structure
- Space: O(?) — WHY: name what's consuming memory and why

**🔄 Variants — How they can twist this:**
- Bullet list of follow-up / variant problems the interviewer may pivot to
- Include LC numbers for known variants

**❓ Cross-Questions:**
- Bullet list of likely interviewer follow-ups with brief answers
- "What if the input is too large for memory?" → ...
- "Can you do better?" → ...
- "How would you test this?" → edge cases
```

---

## When a Problem Is Already Covered Elsewhere

If a solution already exists in `DSA/` or `LLD/` playbooks:
- Write a **5-line recap** (problem + key insight + approach + complexity)
- Add a **cross-reference link** to the full note
- Still include the Variants and Cross-Questions sections (these are Confluent-specific)

Do NOT write just a bare link — Kapil should never need to leave this folder during revision.

---

## Code Hygiene (from universal AGENTS.md)

- Always declare the language on the fence: ` ```java `
- One statement per line
- Always brace `if`/`for`/`while` bodies
- Spaces around operators
- Inline comments on their OWN line (above the statement, not end-of-line)
- No `System.out.println` — use `log.info()` or omit output
- No `...` placeholders — working code only
- `@Override` on every overridden method

---

## Complexity Derivation Rules

- **Never** write just "O(n²)" — always explain WHY
- Good: "O(n²) — two nested loops each iterate n elements; inner loop does O(1) work per iteration"
- Good: "O(m×n) — DP table has m rows × n columns; each cell is filled once in O(1)"
- Bad: "O(n²) — quadratic"
- For space: name WHAT is consuming memory — "O(n) — the HashMap stores at most n entries"
