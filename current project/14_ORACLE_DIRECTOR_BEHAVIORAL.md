# 14 — Oracle Health: Director / Final Behavioral Round
### One-hour prep. Read once, out loud, the night before.

> **The round:** Final interview with a Director. Behavioral / motivational.
> Little to no coding. This is a fit + judgment + impact conversation.
>
> **The role:** EHR/RCM AI Engineer, IC3 (senior individual contributor).
> **Your edge:** 5 years PNS domain depth + you built a production agentic AI system.
> **Reading time:** 60 minutes. The last 20 must be spoken aloud, not read.

---

## Index

| # | Section | What you get |
|---|---|---|
| 1 | [What This Round Actually Is](#1--what-this-round-actually-is) | Who the director is, what they're grading |
| 2 | [The STAR Framework (fast refresh)](#2--the-star-framework-fast-refresh) | The one structure to use for every answer |
| 3 | [Your Story Bank (6 core stories)](#3--your-story-bank-6-core-stories) | Pre-built STAR stories from YOUR real work |
| 4 | [Question → Which Story Map](#4--question--which-story-map) | Any question → which story to reach for |
| 5 | [The Likely Questions + How to Answer](#5--the-likely-questions--how-to-answer) | 15 questions with approach + what to say |
| 6 | [Why Oracle Health? (the make-or-break answer)](#6--why-oracle-health-the-make-or-break-answer) | Mission-aligned, specific, memorized |
| 7 | [Questions YOU Ask the Director](#7--questions-you-ask-the-director) | 6 sharp questions that signal seniority |
| 8 | [Do's, Don'ts & Final Checklist](#8--dos-donts--final-checklist) | The 10-minute pre-interview scan |

---

## 1 — What This Round Actually Is

```
WHO:   A Director — likely cross-functional, maybe not your direct team.
       Oracle runs this like Amazon's "Bar Raiser" (internally the
       "Bartender" round). They have veto power on the hire.

FORMAT: 45–60 min. Conversational. Mostly behavioral + motivational.
        May include ONE light design/scenario question. Little/no coding.

WHAT THEY GRADE (the real rubric):
  1. IMPACT       → Did your work matter? Can you quantify it?
  2. OWNERSHIP    → Do you drive things, or wait to be told?
  3. JUDGMENT     → How do you decide under ambiguity / with trade-offs?
  4. COLLABORATION→ Conflict, feedback, teaching, different personalities
  5. FIT + DRIVE  → Why Oracle Health? Why now? Are you coachable / humble?

THE ONE-LINE MENTAL MODEL:
  "Be a human more than an engineer."
  The director already knows you can build. They're deciding
  whether they want you in the room when things go wrong.
```

**The trap to avoid:** treating this like a tech round. Do not dive into
LangChain4j internals. The director wants the *story around* the work —
the people, the decision, the setback, the result.

---

## 2 — The STAR Framework (fast refresh)

Oracle explicitly recommends STAR. Every story you tell uses it.

```
  S — SITUATION   Set the scene in 2 sentences. Context + stakes.
  T — TASK        What was YOUR specific responsibility? (not "we")
  A — ACTION      What did YOU do? 3–4 concrete steps. This is 60% of the answer.
  R — RESULT      The outcome. QUANTIFY IT. Then: what you learned.

TIMING: 90 seconds to 2 minutes per story. Not 5. Not 30 seconds.

THE TWO MOST COMMON MISTAKES:
  ❌ Living in Situation — 45 seconds of context before the point
  ❌ Saying "we" — the director can't tell what YOU did

THE FIX:
  ✓ 2 sentences of Situation, then move
  ✓ In the Action, say "I" — "I decided," "I built," "I convinced"
  ✓ End every story with a number AND a lesson
```

**The "impact" rule Oracle repeats:** *"How did something specifically benefit
from having you engaged?"* — Bake a number into every story: time saved,
errors reduced, people unblocked, revenue protected.

---

## 3 — Your Story Bank (6 core stories)

These are built from your real PNS/AI work. Memorize the SHAPE, not the words.
Six stories cover ~90% of behavioral questions.

---

### STORY 1 — "The Proud Contribution" (your flagship)
**Use for:** proudest work, most interesting project, biggest impact, technical leadership

```
S: PNS on-call engineers were burning 15–25 minutes per incident manually
   querying 4–5 systems (DCC, Wakanda, Cassandra, Unified Promise) just to
   diagnose why an offer wasn't getting 2-day delivery — at 2am, under pressure.

T: I owned the PNS domain layer of a new AI assistant — the piece that
   required actually knowing how PNS works, not just AI plumbing.

A: - I designed and implemented 40+ tools that expose our PNS APIs to the AI agent.
   - I wrote each tool's description precisely enough that the LLM reliably
     picks the RIGHT one — the descriptions are a correctness mechanism, not docs.
   - I authored the system prompt that encodes PNS debugging logic — the order
     to check systems, market-specific defaults for Mexico and Canada.
   - I validated against 20 real past incidents where we knew the root cause.

R: Investigation time dropped from 15–25 minutes to 8–12 seconds, and the
   answer included the root cause and a fix, not just raw data. What I learned:
   the hardest part wasn't the AI — it was translating 5 years of domain
   intuition into rules a machine could apply consistently.
```

---

### STORY 2 — "The Hardest Problem" (judgment under ambiguity)
**Use for:** hardest technical challenge, ambiguity, a time you were stuck

```
S: Early on, the AI kept calling the wrong tool. We had three carrier-related
   tools — getCarrierData, getCarrierMethodInfo, getMcseCarrierInfo — all "about
   carriers." The LLM picked randomly, which produced confidently wrong answers.

T: I had to make the agent reliably pick the correct tool without any way to
   "debug" an LLM's reasoning directly.

A: - I treated tool descriptions like function contracts written for an LLM.
   - I built a test loop: run real PNS scenarios, observe which tool it called,
     and when it chose wrong, trace it to a vague or overlapping description.
   - I rewrote descriptions to disambiguate — "use THIS when you need max SLA
     for a carrier method" vs. "use THIS for top-level carrier config."
   - I made tools return structured errors instead of throwing, so the LLM
     wouldn't hallucinate when an API was down.

R: Tool-selection accuracy went from unreliable to consistent across our test
   scenarios. The lesson: with LLMs, precision of language IS engineering —
   ambiguity in a description is a production bug, not a style choice.
```

---

### STORY 3 — "The Failure / Mistake" (humility + growth)
**Use for:** tell me about a failure, a mistake, what you'd do differently

```
S: In our first validation pass, we tested tools only end-to-end — through
   the full agent. When the AI gave a wrong answer, I couldn't tell if the
   tool returned bad data or the LLM misread good data.

T: I was responsible for proving the domain layer was correct, and my testing
   approach was slowing everyone down — iterations took days.

A: - I owned the miss openly with the team rather than defending the approach.
   - I retrofitted isolated tests for each tool with mocked API responses so
     we could separate "tool returned wrong data" from "LLM misinterpreted it."
   - I added structured logging around tool selection so failures were traceable.

R: The validation loop went from days to hours. The lesson I carry: build the
   observability to isolate failures BEFORE you scale the system, not after.
   If I'd invested a week in a test harness up front, I'd have saved a month.
```

> **Why this story works:** it's a real, non-fatal mistake, you owned it, you
> fixed it systemically, and the lesson is senior-level (observability first).

---

### STORY 4 — "Conflict / Different Personality" (collaboration)
**Use for:** conflict, disagreement, working with someone different from you

```
S: The AI-infra engineers wanted tools to return large, rich JSON blobs — more
   data, fewer calls. I wanted small, focused tools. We disagreed, and both of
   us had defensible reasons.

T: We had to settle the tool-granularity design without it becoming a standoff —
   the whole agent's reliability depended on getting it right.

A: - Instead of arguing in the abstract, I proposed we test both empirically.
   - I ran the same scenarios with large blobs vs. focused tools and showed the
     LLM performed measurably better with small, single-purpose tools.
   - I acknowledged their real concern — call overhead — and we addressed it with
     parallel tool execution rather than bigger payloads.

R: We aligned on focused tools with a plan for their concern. The lesson: turn
   a personality/opinion clash into a data question — it depersonalizes the
   disagreement and the best answer usually wins on its own.
```

---

### STORY 5 — "Teaching / Sharing Knowledge" (leverage)
**Use for:** teaching a colleague, sharing learnings, mentoring, lifting the team

```
S: Once the agent worked, other domain teams (beyond PNS) wanted to build
   their own tools for it — but tool-description quality made or broke it, and
   that lesson lived only in my head.

T: I needed to transfer "how to write a tool the LLM actually uses correctly"
   so others didn't repeat the disambiguation pain I'd gone through.

A: - I wrote a short guide with concrete good vs. bad description examples.
   - I walked a couple of engineers through the test-and-revise loop live,
     using their own tools as the material.
   - I framed the principle simply: "write the description for the LLM, not
     for the human reading the code."

R: Other teams onboarded their tools faster and with fewer wrong-tool bugs.
   The lesson: the highest-leverage thing a senior IC does is turn a hard-won
   lesson into something the team can reuse without you in the room.
```

---

### STORY 6 — "Ownership / Going Beyond Scope" (drive)
**Use for:** ownership, initiative, going above and beyond, no one told you to

```
S: The system prompt kept producing inefficient reasoning — the AI checked
   Wakanda inventory before carrier restrictions, which is backwards for most
   PNS failures. Nobody had flagged this; it wasn't "my ticket."

T: It wasn't assigned to me, but I knew the domain well enough to see the AI
   was reasoning in the wrong order and wasting tool calls.

A: - I took the initiative to encode explicit priority ordering into the prompt:
     template mapping first, then carrier restrictions, then zone, inventory last.
   - I added the Mexico postal-code default (06600) because on-call queries rarely
     specify one and the AI was failing tools that required it.
   - I drove these from watching the agent get real questions wrong, not from a spec.

R: The agent reached correct root causes faster and with fewer wasted calls.
   The lesson: domain ownership means fixing what you can see is wrong, even
   when it's not on your board — that's the difference between a task-doer and
   an owner.
```

---

## 4 — Question → Which Story Map

When a question lands, reach for the mapped story. Don't improvise a new one.

```
┌──────────────────────────────────────────────┬──────────────────┐
│  If they ask about...                        │  Reach for...     │
├──────────────────────────────────────────────┼──────────────────┤
│  Proudest work / best project / impact       │  Story 1          │
│  Hardest problem / ambiguity / being stuck    │  Story 2          │
│  A failure / mistake / do-over                │  Story 3          │
│  Conflict / disagreement / difficult person   │  Story 4          │
│  Teaching / mentoring / sharing learnings     │  Story 5          │
│  Ownership / initiative / above-and-beyond    │  Story 6          │
│  Giving feedback                              │  Story 4 or 5     │
│  Technical leadership                         │  Story 1 or 6     │
│  Handling a tight deadline / pressure         │  Story 1 (2am)    │
│  Working cross-functionally                   │  Story 4          │
└──────────────────────────────────────────────┴──────────────────┘
```

**If you get a question no story fits:** buy 3 seconds ("Let me think of a good
example"), then adapt the CLOSEST story. Never fabricate — directors probe, and
invented stories collapse under follow-ups.

---

## 5 — The Likely Questions + How to Answer

These are the questions actually reported for Oracle / Oracle Health final rounds.

---

**Q1: "Tell me about yourself."**
> Approach: 90 seconds. Arc: who you are → what you've done → why this role now.
> Say: "I'm a software engineer with ~5 years in Walmart's Promise & Shipping
> platform — deep domain work on fulfillment and delivery systems. Most recently
> I built the domain layer of a production AI agent that lets on-call engineers
> debug in plain English instead of manually querying five systems. That work —
> combining deep domain knowledge with LLMs and agent frameworks — is exactly
> why the Oracle Health role caught my attention: same pattern, applied to a
> domain that matters more, healthcare."
> Don't: recite your resume top to bottom. Land the arc, then stop.

---

**Q2: "Walk me through a contribution you're especially proud of."**
> Story 1. Lead with the human problem (2am debugging), not the tech.

---

**Q3: "Tell me about a time you failed / made a mistake."**
> Story 3. Own it in the first sentence. Spend most of the time on the fix and
> the lesson. Directors want to see you're safe to fail around — not flawless.

---

**Q4: "Tell me about a conflict with a teammate."**
> Story 4. Key move: show respect for the other person's reasoning, then how you
> turned it into a data question. Never make the other person the villain.

---

**Q5: "Tell me about a time you gave someone difficult feedback."**
> Adapt Story 5. Frame: private, specific, about the work not the person, and
> you followed up. Example: guiding an engineer whose tool descriptions were too
> vague — you showed the failure live rather than just saying "these are wrong."

---

**Q6: "Tell me about a time you had to teach someone something complex."**
> Story 5. Emphasize you simplified it to a single principle ("write for the LLM,
> not the human") — teaching is compression, and that signals senior thinking.

---

**Q7: "Describe working with someone whose personality was very different."**
> Story 4. The infra engineers were payload-optimizers; you were reliability-first.
> Different instincts, resolved by testing both.

---

**Q8: "Tell me about a time you were unsure how to solve something."**
> Story 2. The LLM-picks-wrong-tool problem — you couldn't "debug" an LLM, so you
> built an empirical loop. Show comfort with not knowing, then a method to find out.

---

**Q9: "Tell me about a time you took ownership beyond your role."**
> Story 6. The reasoning-order fix nobody assigned you.

---

**Q10: "How do you handle ambiguous or unclear requirements?"**
> Approach: this maps to the JD line "translate ambiguous requirements into
> scalable solutions." Say: "I start by finding the real user pain, not the
> stated feature. With the AI agent, the ask was vague — 'make debugging easier.'
> I grounded it by taking 20 real past incidents and asking: could the system
> solve these? That turned an ambiguous goal into a concrete, testable target."

---

**Q11: "Tell me about a tight deadline or high-pressure situation."**
> Use the 2am on-call framing from Story 1 — the users themselves are under
> pressure, and you built for that context: precise, fast, root-cause-first.

---

**Q12: "How do you deal with a difficult stakeholder / customer?"**
> Frame the on-call engineers as your customers. You validated the AI's answers
> against cases they'd already solved, so they'd TRUST it — earning adoption from
> skeptical expert users was the real challenge, not the code.

---

**Q13: "Why are you leaving your current role / Why leave Walmart?"**
> Approach: positive, forward-looking. Never trash the current job.
> Say: "I've had a great run and deep impact in PNS. But I've now seen what AI
> agents can do for a domain, and I want to apply that where the stakes are
> higher — healthcare. Oracle Health is doing exactly this at scale, and I want
> to grow in an AI-first org rather than being the AI person on a logistics team."

---

**Q14: "Where do you see yourself in a few years?" / "What are you looking for?"**
> Say: "Growing as a senior engineer who bridges deep domain knowledge and
> applied AI. I'm less interested in switching domains every year and more in
> going deep — healthcare is a domain worth investing years in, and I want to
> become someone the team relies on for both the AI and the domain judgment."

---

**Q15: "Do you have any questions for me?"**
> ALWAYS have 3+. See Section 7. Ending with "no questions" reads as low interest.

---

## 6 — Why Oracle Health? (the make-or-break answer)

Oracle says: prepare this deeper than you did for the recruiter. A weak
"Why Oracle Health" sinks otherwise strong candidates. Memorize this.

```
THE THREE PILLARS (weave these in):

  1. MISSION THAT MATTERS
     Oracle Health's mission is to reduce clinician administrative burden —
     literally "get rid of the clicks" — so clinicians spend time with patients,
     not screens. That maps DIRECTLY to what I already do: I built AI that
     collapsed 20 minutes of manual work into seconds. Same mission, higher stakes.

  2. THE WORK IS WHAT I'M BEST AT
     This role is LLMs + agents applied to a complex domain (EHR/RCM). That's
     exactly the intersection I've been operating in — I owned the domain layer
     of an agentic system. I'm not pivoting into something new; I'm applying my
     strongest skill to a domain worth investing in.

  3. SCALE + SERIOUSNESS
     Oracle Health is migrating 1,000+ EHR customers to cloud, pushing
     interoperability (Seamless Exchange, CMS Aligned Network status), and
     embedding AI into clinical workflows. It's an AI-first, cloud-first org
     doing this at national scale — I want to build where AI is the core of
     the product, not a side project.
```

**The 45-second spoken version:**
> "Two reasons. First, the mission — reducing clinician burden so they spend
> time with patients instead of paperwork. That's the same thing I've been doing
> in logistics: using AI to collapse manual, multi-system work into seconds. But
> healthcare matters more, and I want my work to matter more. Second, the work is
> squarely what I'm strongest at — LLMs and agent frameworks applied to a hard,
> real domain. At Oracle Health that's the core of the product, not a side
> experiment. I'd rather go deep in a domain worth years than stay a generalist."

**If they push "why not stay in your current AI work?":**
> "Because there, AI is a tool bolted onto logistics. At Oracle Health, AI in
> healthcare IS the mission. I want to be in an org where this is the main thing,
> surrounded by people going deeper than I can go alone."

---

## 7 — Questions YOU Ask the Director

Pick 3. Directors judge you by your questions as much as your answers.
These signal seniority, ownership, and genuine interest.

```
ON THE PROBLEM / STRATEGY:
  1. "Where is AI actually delivering value in the product today versus where
      it's still early — clinical documentation, coding, prior auth?"
      → Signals you understand the real use cases (from files 12 & 13).

  2. "In healthcare AI, correctness has clinical consequences. How does the
      team think about the line between AI-assist and full automation?"
      → Signals maturity — you know hallucination matters more here.

ON THE TEAM / GROWTH:
  3. "For someone joining at this level, what separates a good first year from
      a great one in your eyes?"
      → Signals you're already thinking about impact, not just landing the job.

  4. "How does the team balance shipping AI features fast against the
      evaluation and safety bar that healthcare demands?"
      → Signals you get the core tension of the domain.

ON THE DIRECTOR / CULTURE:
  5. "What's kept YOU at Oracle Health? What makes this team worth your time?"
      → Personal, human, and directors love answering it.

  6. "What's the hardest problem the team is wrestling with right now that a
      new senior engineer could actually help move?"
      → Signals you want to contribute to real problems immediately.
```

**Avoid asking:** compensation, WFH policy, "what does the team do?" (shows you
didn't research), or anything answered on the careers page.

---

## 8 — Do's, Don'ts & Final Checklist

```
DO:
  ✓ Be a human first, engineer second — warmth, humility, curiosity
  ✓ Use STAR. Every story. 90s–2min each.
  ✓ Quantify every result (seconds, %, count, hours saved)
  ✓ Say "I" for your actions, "we" only for genuine team credit
  ✓ Own your failure story fully — no deflection
  ✓ Show you know the domain (EHR, RCM, prior auth, coding) — see files 11–13
  ✓ Connect every answer back to impact on real users
  ✓ Have your "Why Oracle Health" memorized cold
  ✓ End with strong questions for the director

DON'T:
  ✗ Dive into LangChain4j/Milvus internals — wrong round for it
  ✗ Trash Walmart or your current team
  ✗ Say "we" so much the director can't find YOU in the story
  ✗ Ramble — if you're past 2 minutes, land the plane
  ✗ Claim you built the whole AI system — own the DOMAIN layer honestly
  ✗ Fabricate a story — probing will expose it
  ✗ Answer "why Oracle" with something generic ("big company, good product")
  ✗ Say "I don't have any questions"
```

**The 10-minute pre-interview scan (read right before):**
```
  1. Say your "Tell me about yourself" out loud once.        (90 sec)
  2. Say your "Why Oracle Health" out loud once.             (45 sec)
  3. Recall the 6 story titles + their one-number result.    (2 min)
  4. Recall the Question→Story map.                          (2 min)
  5. Pick your 3 questions for the director.                 (1 min)
  6. Breathe. Remember: they already know you can build.
     This round is about whether they want you in the room.
```

---

## THE ONE THING TO REMEMBER

> The director is not testing whether you can code. That's settled.
> They're deciding: *"When this person hits a hard problem, a conflict, or a
> failure — do I want them on my team?"*
> Every story you tell should quietly answer: **yes.**

---

**Sources for the research behind this file:**
- [Exponent — Oracle Software Engineer Interview Guide](https://www.tryexponent.com/guides/oracle-software-engineer-interview)
- [Exponent — Oracle Interview Process](https://www.tryexponent.com/blog/oracle-interview-process)
- [Oracle Blog — How to prepare for behavioral interview questions](https://blogs.oracle.com/jobsatoracle/how-to-prepare-for-behavioral-interview-questions)
- [InterviewHelp — What to expect on Oracle Health's EM interview](https://www.interviewhelp.io/blog/posts/what_to_expect_on_oracle_healths_em_interview/)
- [FinalRoundAI — Oracle Recruitment Process + Interview Tips](https://www.finalroundai.com/blog/oracle-interview-process)
- [Oracle Health — Electronic Health Record](https://www.oracle.com/health/clinical-suite/electronic-health-record/)
- [Becker's — 35 things to know about Oracle Health](https://www.beckershospitalreview.com/healthcare-information-technology/ehrs/35-things-to-know-about-oracle-health/)
