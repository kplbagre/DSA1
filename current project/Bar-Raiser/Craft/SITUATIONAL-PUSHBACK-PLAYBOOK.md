# Situational Pushback Playbook
### Real, high-pushback questions — answered with the pushback chain + the instinct to suppress

> **Why this exists.** Across the Confluent value-fit round *and* a friend's SMTS round, the pattern was identical: the **first answer was fine, the pushback broke it.** This file targets exactly that — real situational/interpersonal questions that carry a pushback chain, each with:
> - ❌ **Instinct to suppress** — the natural gut answer that tanks it (catch yourself *before* you say it)
> - ✅ **Core principle** — the one line that anchors the answer
> - **How I'd work it** — the actual approach
> - ⚠️ **Pushbacks → answers** — the follow-up chain, scripted
> - **Theme / value mapping**
>
> **This is a living collection.** Every time you or a friend gets pushed on something, drop it in §2 (see the collection protocol in §3).
>
> **Curation rule:** only *real, actually-asked, high-pushback* questions. Not generic lists. Ten curated beat a hundred generic.
>
> **Companions:** the two reusable answer patterns live in [SALESFORCE-HM-BEHAVIORAL-DEEPDIVE.md](SALESFORCE-HM-BEHAVIORAL-DEEPDIVE.md); framing stories in [BAR-RAISER-BEHAVIORAL.md](BAR-RAISER-BEHAVIORAL.md).

---

## 1. The Pushback Meta-Moves (internalize these — they handle questions you never rehearsed)

You can't memorize every question. You *can* internalize the ~6 moves that answer almost any pushback:

1. **Separate the person from the problem.** Attack the problem; never the person. "The deliverable is at risk," not "X is failing."
2. **Assume good intent first.** Reach for the benign explanation (OOO, overwhelmed, misunderstanding) before blame. It's almost always right, and it's never held against you.
3. **Escalate the RISK, not the PERSON — and never by surprise.** Raise "this is at risk, I need help," not a complaint. If you flag something involving a person, tell that person you're flagging it. No blindsides.
4. **Make it about data or risk, not preference.** Turn "I disagree" into "here's the specific risk / here's what the data shows." An opinion clash becomes a shared question the best answer wins.
5. **Own it, then close the loop.** For any failure: no excuses, no blame-shift — then say what changed *systemically* so it can't recur (the prevention beat). "I fixed it" is half an answer.
6. **Disagree, then commit.** Voice the concern once, with reasoning; ask for a cheap guardrail; then support the decision fully — no sabotage, no "I told you so."

### 🚫 Universal DON'Ts (suppress these instincts in ANY answer)

- ❌ Blaming a person, another team, or "the old code / the previous dev."
- ❌ Playing the martyr — "I just did the whole thing myself." (Reads as: hides problems, doesn't scale, doesn't escalate.)
- ❌ Answering "nothing" to *"what would you do differently?"*
- ❌ Getting defensive or arguing tenure on a leveling probe.
- ❌ Trashing your current employer (fails Trust instantly).
- ❌ Vague "I worked really hard" with no *decision* in the story.
- ❌ Claiming you were 100% right / never wrong / never lose composure.

---

## 2. The Question Bank

---

### Q1 — "You and one other person are jointly assigned an important project. That person stops replying to your messages/emails for several days. How do you resolve it?"
*Theme 3 — proactive communication / unblocking · maps to Trust + One Team*

**❌ Instinct to suppress:**
- "They're ignoring me, so it's on them." (blame)
- "I'd just do the whole thing myself." (martyr — hides the problem)
- "I'd escalate to my manager right away." (escalating as complaint, too early, by surprise)

**✅ Core principle:** "I separate two things and handle them in parallel — **protect the deliverable** and **understand the person** — without letting either block the other. The failure mode is doing only one: silently absorbing it until it explodes, or escalating as blame. I do neither."

**How I'd work it:**
- **Assume good intent first** — a few days of silence is usually OOO, sick, a personal emergency, or drowning in another fire; not ignoring me.
- **Escalate the *channel* before the *person*** — email silence ≠ unreachable. Try Slack, a direct call, their hours/timezone, the team channel, walking over / a quick video ping.
- **Keep the project moving** — make progress on what I can, document state so nothing stalls.
- **If still silent after genuine direct attempts, escalate the *risk*, not the person** — "this shared deliverable is at risk, I can't reach [X] for N days, can you help?" And I tell X I've flagged it. No surprise.
- **No ambush on return** — lead with "what happened / are you okay," then re-plan.

**The judgment line:** "The trigger to escalate isn't how annoyed I am — it's *whether the silence is putting the deliverable at risk*. If it threatens the critical path, that's day 2, not deadline day."

**⚠️ Pushbacks → answers:**

| Pushback | Answer |
| --- | --- |
| "Still no response after all channels?" | "Escalate to my manager with project-risk framing, and keep delivering my part. I don't let the project die waiting — and I don't hide that a co-owner is unreachable." |
| "Isn't going to the manager throwing them under the bus?" | "No — I frame it as 'the deliverable is at risk and I can't reach my partner,' not 'X is ignoring me,' and I tell X I raised it. A shared deliverable's health is legitimately the manager's business." |
| "Why not just do it all yourself?" | "For a few days I pick up what I can — but silently absorbing a co-owner's whole scope hides a real problem and denies the manager the chance to reallocate. Ownership is surfacing risk, not quietly burning out." |
| "How long before you escalate?" | "Tied to risk, not a clock. Blocks my critical path → day 2. Slack in the schedule → a bit longer." |
| "Turns out they were just busy — overreacted?" | "No harm done — which is exactly why I lead with benign assumption and gentle channels before any escalation. I never open with blame." |
| "What if they're senior to you?" | "Same principle, softer tone. I still surface the project risk — influence, not authority." |
| "Manager says 'just handle it'?" | "Then I re-scope with them explicitly — what ships with one person, what slips — so it's a shared, informed decision, not me over-committing silently." |

---

### Q2 — "A senior asks you to do something you strongly believe won't work / isn't viable. What do you do?"
*Theme 2 — principled dissent (upward) · maps to Trust (backbone)*

**❌ Instinct to suppress:**
- "They're senior, so I just do it." (no backbone — they're probing whether you'll flag a known problem)
- "I'd refuse / tell them they're wrong." (no commit, no humility)

**✅ Core principle:** "I voice the concern with a *specific risk*, not a preference; propose a cheap way to de-risk it; and if I'm still overruled, I disagree-and-commit — genuinely."

**How I'd work it:**
- **Understand their reasoning first** — ask why; they may have context I don't. Half the time this resolves it.
- **State the *specific* risk, with evidence** — "here's what breaks and who it affects," not "I don't like this."
- **Propose a guardrail** — a small trial, a flag, a rollback path, a checkpoint — so the risk is reversible and cheap to test.
- **If overruled, commit fully** — no sabotage, no seeds of "told you so." I note my concern neutrally (for team learning, not self-protection) and I monitor, so if the risk shows up we can unwind fast.

**⚠️ Pushbacks → answers:**

| Pushback | Answer |
| --- | --- |
| "What if they overrule you?" | "I commit — with a cheap guardrail in place (flag / checkpoint) so if the risk materializes we catch it early and correct with data." |
| "What if you turn out to be right?" | "No I-told-you-so. I surface it calmly with the data and focus on the fix. Being right isn't the point; the outcome is." |
| "Isn't putting your concern in writing just CYA?" | "It's for the team to learn from, not to protect myself — neutral, factual, so we improve the decision next time." |
| "What if it's an ethics/safety issue, not just a bad idea?" | "Different category — that I escalate and don't simply comply. Disagree-and-commit is for judgment calls, not for things that are wrong." |

---

### Q3 — "Teammate takes credit for your work." / "Your contribution gets attributed to someone else."
*Emotional regulation + fair attribution · maps to Trust + One Team*

**❌ Instinct to suppress:**
- "Call them out publicly / in the meeting." (turf war, reads as ego)
- "Say nothing and stew." (resentment, unresolved)
- Assuming malice.

**✅ Core principle:** "I address it privately, assume a mix-up before malice, and keep the focus on an accurate record — not on winning."

**How I'd work it:**
- **Private, low-key first** — "I think there was a mix-up on who did X, wanted to sync." Most of the time it's genuine, not theft.
- **Make my work visible through normal artifacts going forward** — design docs, PRs, standup updates — so contribution is on the record without a fight.
- **Escalate only if it's a persistent pattern that's affecting the team's picture** — framed neutrally to my manager as wanting accurate attribution, not as an accusation.

**⚠️ Pushbacks → answers:**

| Pushback | Answer |
| --- | --- |
| "What if they deny it?" | "I don't litigate it. I make my contributions visible through artifacts from here on — the record speaks louder than a dispute." |
| "Isn't letting it go just letting them win?" | "The goal is an accurate record and a working relationship, not winning. Artifacts and repeated delivery settle it more durably than confrontation." |
| "When would you involve your manager?" | "Only if it's persistent and materially skewing how the team's work is seen — and then neutrally, about attribution, not about the person." |

---

### Q4 — "You strongly disagree with a decision that's already been made. What do you do?"
*Theme 2 — disagree & commit · maps to One Team + Trust*

**❌ Instinct to suppress:**
- Re-litigating it repeatedly / passive-aggressive compliance.
- Bad-mouthing the decision to peers.
- Sandbagging so it fails and proves your point.

**✅ Core principle:** "Voice it once through the right channel, then commit fully and visibly. Undermining a made decision is far more corrosive than the decision itself."

**How I'd work it:**
- **Make sure my concern was actually heard** — once, with reasoning, to the right person.
- **If the call stands, commit genuinely** — support it publicly even though I argued against it privately; no sabotage, no seeded doubt.
- **Propose a checkpoint** — "let's revisit at milestone X with these metrics" — so if my concern is real, it surfaces as *data*, not opinion.

**⚠️ Pushbacks → answers:**

| Pushback | Answer |
| --- | --- |
| "What if it's genuinely heading to fail?" | "Then I build the metric/checkpoint that surfaces it early, so we correct with evidence rather than re-arguing opinions." |
| "Isn't committing to something you think is wrong dishonest?" | "I commit to the *decision process*, having voiced my view. What's dishonest is undermining a call I publicly accepted." |
| "What if it's an ethics/safety problem?" | "Different category — that I escalate, I don't just commit. Disagree-and-commit is for judgment calls." |

---

### Q5 — "Tell me about a time you lost your temper / lost composure."
*Theme 1 — emotional regulation*

**❌ Instinct to suppress:**
- "I never lose my temper." (unbelievable — reads as low self-awareness)
- Blaming the trigger/person for making you snap.

**✅ Core principle:** self-aware, own it, and show a *recovery mechanism* — they want a human who manages it, not a robot.

→ **Full answer + options:** [BAR-RAISER-BEHAVIORAL.md](BAR-RAISER-BEHAVIORAL.md) `Q16`. Pick the true option; the recovery mechanism (time-boxing / switch-to-live / comms-role) is what's scored.

---

### Q6 — "What's a perception people have of you that you think is wrong?" / "How would colleagues describe you?"
*Theme 5 — self-awareness*

**❌ Instinct to suppress:**
- The humble-brag ("people think I work too hard / care too much").
- A safe non-answer that dodges a real blind spot.

**✅ Core principle:** a *genuine* blind spot others actually see, plus what you did about it. The insight must feel earned.

→ **Full answer + options:** [BAR-RAISER-BEHAVIORAL.md](BAR-RAISER-BEHAVIORAL.md) `Q18`.

---

### Q7 — "Tell me about a time you couldn't meet a deadline." *(a real miss)*
*Theme 4 — accountability*

**❌ Instinct to suppress:**
- Blaming changed requirements / other teams / "not enough time."
- Claiming you never miss deadlines.
- Using a near-miss you *saved* instead of a real miss.

**✅ Core principle:** own the miss cleanly, root-cause it (including your part), and show what changed *systemically* after.

→ **Full answer + options:** [BAR-RAISER-BEHAVIORAL.md](BAR-RAISER-BEHAVIORAL.md) `Q19`.

---

## 3. Collection Protocol (keep this file growing)

When you or a friend gets pushed on a question:
1. **Write the exact question** (as asked) at the bottom of §2.
2. Fill the template: ❌ instinct to suppress → ✅ core principle → how I'd work it → ⚠️ pushbacks table → theme/value.
3. Tie it back to a **meta-move** in §1 — if it doesn't map to one, that's a signal you've found a *new* move worth adding to §1.
4. Curation check: is it *real and high-pushback*? If it's a generic question with no follow-up teeth, it doesn't belong here.

**Backlog — real questions to add when you have the details** (heard-of but not yet scripted): "a time you had to give someone hard feedback," "two seniors give you conflicting direction," "you're asked to cut corners to hit a date," "a decision you made with incomplete information that went wrong."

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| Aug 28, 2026 | **File created.** Pushback playbook: 6 meta-moves + universal DON'Ts, then a seeded bank of 7 real high-pushback questions each with the "instinct to suppress," core principle, approach, and scripted pushback chain (unresponsive co-owner fully written; senior-asks-unviable, credit-stealing, disagree-with-made-decision fully written; composure/self-perception/missed-deadline cross-linked to BAR-RAISER-BEHAVIORAL Q16/Q18/Q19). Collection protocol + backlog added. |
