# Notes Standards — SystemDesignConcepts

> **Purpose:** Every concept note in this folder follows this exact structure. Define the standard once, write consistently forever. Review this before writing any new note.

---

## 🎯 Design Principles Behind This Format

1. **Mental model before mechanics.** The concept sticks in memory through analogy and intuition, not through algorithm steps.
2. **Real company names, not "imagine a system."** Swiggy, Razorpay, BookMyShow — not "Company X."
3. **Interview Q&A is the payoff.** The rest of the note builds toward this. These are the actual words you say in the room.
4. **ASCII visuals are mandatory** for any concept with state, flow, or sequence (AGENTS.md Rule 6).
5. **Lightweight, not academic.** Each note should be revisable in 20 minutes before an interview.

---

## 📐 Note Structure (Exact Section Order)

Every note has these sections, in this order:

---

### Section 1 — 🎯 Why This Matters
**Length:** 3-5 lines maximum.

- One line: what problem this concept solves
- One line: which interview round it shows up in
- One line: why a senior engineer is expected to know this

**Do NOT:** Explain the concept here. Just establish why the reader should care.

---

### Section 2 — 🧠 The Mental Model
**Length:** 5-10 lines.

- One everyday analogy that makes the concept stick. Preferably something physical — a lock, a queue, a bucket, a key, a token.
- The analogy should cover the core mechanic, not just the name.
- End with one sentence: "The key insight is: ___."

**Do NOT:** Dive into technical mechanics here. Save that for Section 4.

---

### Section 3 — 🎨 Visual — How It Works
**Mandatory.** No exceptions for concepts with state, flow, or sequence.

Format:
````markdown
### 🎨 Visual — <one-line description of what the diagram shows>

```
(ASCII diagram — max 80 columns wide)

KEY INVARIANT:
   <one or two lines naming the algorithmic/design property the picture teaches>
```
````

Use:
- `→ ← ↑ ↓` for arrows
- `┌ ┐ └ ┘ ├ ┤ ─ │` for boxes
- `✅ ❌` for success/failure markers

---

### Section 4 — ⚙️ How It Actually Works
**Length:** 20-40 lines including code.

**Steps in plain English FIRST** (AGENTS.md Rule 2), then code.

Format:
````markdown
**Steps:**
1. **Step one** — what happens and why.
2. **Step two** — what happens and why.
3. **Step three** — what happens and why.

```java
// code with comments matching the numbered steps
```
````

Code rules (AGENTS.md Rule 1):
- Language tag: always ` ```java `
- One statement per line
- Always braced (`if`, `for`, `while`)
- Spaces around operators
- Working code only — no `...` placeholders

---

### Section 5 — 🏢 Real World — Where Companies Use This
**Length:** 4-6 bullet points.

Format per bullet:
```
- **CompanyName** (product/feature): why they use THIS concept specifically.
```

Requirements:
- Real company names — Swiggy, Razorpay, Amazon, BookMyShow, Flipkart, PhonePe, Uber, Zomato, etc.
- Real product context — not "they have high traffic" but "during Big Billion Day flash sales where 50K users hit the same last item"
- Show WHY this concept fits their specific constraint

---

### Section 6 — 🧭 When to Use vs When NOT to Use
**Format:** Decision table.

| Use this when | Do NOT use when |
|---|---|
| condition A | condition X |
| condition B | condition Y |

Then: **"The common mistake"** — one line on what engineers get wrong.

---

### Section 7 — ⚠️ Trade-offs
**Three fixed rows:**

| | |
|---|---|
| **You gain** | what this concept gives you |
| **You lose** | what this concept costs you |
| **Failure mode** | what breaks if you apply this in the wrong situation |

---

### Section 8 — 🔬 Interview Q&As
**Mandatory. 4-6 questions minimum.**

Format:
```
### Q: "Exact question the interviewer will ask"
> 2-4 sentence crisp answer. Senior signal in every sentence.
```

Include:
- The basic "what is X?" question
- The "when would you use X?" question
- The "what's the trade-off?" question
- At least one follow-up/probe question
- The worked example question (using a real problem like bus booking, flash sale, etc.)

---

### Section 9 — 🧾 TL;DR — One Interviewer-Ready Line
**One sentence only.** This is what you say when you drop this concept naturally in an interview answer.

Format:
```
> "One sentence that demonstrates you know this concept and its trade-off, suitable for dropping mid-conversation."
```

---

### Section 10 — 🔗 Related Concepts (optional, 3-5 links)
Cross-links to other notes in this folder using relative paths.

---

## ✅ Pre-Publish Checklist

Before finalizing any note, verify:

- [ ] All 9 required sections present (Section 10 is optional)
- [ ] Section 2 has a concrete everyday analogy
- [ ] Section 3 has an ASCII visual with KEY INVARIANT
- [ ] Section 4 has English steps BEFORE code
- [ ] Section 4 code is valid Java (language-tagged, braced, one statement per line)
- [ ] Section 5 has ≥ 3 real company names with real context
- [ ] Section 8 has ≥ 4 Q&As with crisp answers
- [ ] Every potentially-unfamiliar term is glossed at first use (AGENTS.md Rule 8)
- [ ] No emojis outside the approved AGENTS.md palette
- [ ] Note is readable end-to-end in under 20 minutes

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | Standards file created. Format defined before writing any notes. |
