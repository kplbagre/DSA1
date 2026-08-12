# Salesforce LLD — The One Hour Read

> **Purpose:** after reading this folder you can answer *"why are you using that?"* for every
> decision in your six solution files. Not theory. Just the justification behind each choice.
>
> **Every file has the same shape:** the question they ask -> why the answer is what it is ->
> the words you say back.

---

## The one-hour plan

| Order | File | Minutes | What you'll be able to answer |
|---|---|---|---|
| 1 | `01-why-interfaces.md` | 12 | "Why an interface here?" / "Why not just an if-else?" |
| 2 | `02-why-this-relationship.md` | 12 | "Composition or aggregation?" / "Why by ID and not the object?" |
| 3 | `03-why-this-pattern.md` | 15 | "Why Strategy?" / "Why not Observer?" / "Isn't that over-engineering?" |
| 4 | `04-why-this-lock.md` | 12 | "How do you stop two threads doing X?" / "Why not synchronized?" |
| 5 | `05-why-drill.md` | 9 | Rapid self-test — cover the answers, say them out loud |
| — | `00-cheatsheet.md` | 10 | The morning-of refresher (read last, re-read on the day) |

**Total: 60 minutes.** If you only get 20: read `01` and `02`. Those two cover the most
frequently asked follow-ups.

---

## The single idea behind everything

> **You are never graded on knowing a pattern. You are graded on being able to say what
> would break without it.**

Every "why" answer in this folder has the same three-part shape. Learn the shape, and you can
answer questions about designs you've never seen:

```
1. WHAT VARIES        "The thing that changes here is X."
2. WHAT ISOLATES IT   "So X goes behind an interface / into its own class."
3. WHAT BREAKS IF NOT "Without it, adding a new X edits [specific class], which is
                       an OCP violation / a race / a leak of [specific thing]."
```

Part 3 is the scored part. "It's cleaner" scores zero. *"Adding SMS would edit the
orchestrator"* scores.

---

## The five sentences that answer 80% of "why" questions

1. *"Because that's the variation point — it changes, so it goes behind an interface."*
2. *"If I `new` it inside, it's composition; it's injected, so it's aggregation."*
3. *"Without it, adding a new type edits existing code — that's the OCP violation."*
4. *"The read isn't the guarantee; the re-check inside the lock is."*
5. *"It narrows the window, it doesn't close it — [X] is the only complete answer."*

---

## How to use this when they ask something not in here

Fall back to the shape. Out loud:

> *"What varies here is [X]. I've isolated it in [Y]. If I inlined it instead, then
> [specific consequence]."*

If you can't name the specific consequence, you don't have a justification — say so honestly
and reason it out in front of them. **That scores better than inventing a benefit**, because
inventing one collapses on the next follow-up.
