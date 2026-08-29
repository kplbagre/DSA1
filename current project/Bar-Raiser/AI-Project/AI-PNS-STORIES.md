# AI PNS Assistant — Developer Stories (STAR + Pushbacks)
### Your recent AI/LLM work, interview-ready — company-agnostic

> **What this is:** Your work on the PNS AI Assistant, shaped into behavioral stories with the
> decision points made explicit and the likely follow-up pushbacks scripted. Companion to the
> MCSE stories in [MCSE-interview-stories.md](../Core-Project/MCSE-interview-stories.md) and the behavioral bank in
> [BAR-RAISER-BEHAVIORAL.md](../Craft/BAR-RAISER-BEHAVIORAL.md).
>
> **Why it exists:** The Salesforce Signup JD makes **AI fluency a core expectation** ("use AI across
> the full SDLC," "think agentic"). Most candidates can't answer this from real work. You can — this
> file makes sure you claim it *accurately* and defend it under probing.
>
> ⚠️ **Honesty rule (non-negotiable):** Claim the **PNS domain layer** — the tools, the system prompt,
> the conversation handler. The **AI infrastructure** (LangChain4j wiring, Cassandra memory, Milvus/RAG,
> WebSocket, SSO, React frontend) was built by the rest of the team. Never claim the whole app. The
> honest split is *stronger* than over-claiming — it signals you know exactly where the hard, defensible
> value was.
>
> ⚠️ **Scrub rule:** internal service codenames → concepts ("our config/template service," "inventory
> service," "promise-aggregation service"); the internal model → "an internal ~120B model on our Azure
> tenant." Tool method names (`getCarrierMethodInfo`, etc.) are illustrative. In the room you *may* say a
> codename once with its gloss ("our config service, internally DCC"); in this pushed file, keep it scrubbed.

---

## 🧾 The Contribution — One Honest Sentence

> "The team built the AI infrastructure — LangChain4j, Cassandra memory, Milvus, WebSocket, auth.
> I owned the **PNS domain layer**: 40+ tools that expose our PNS APIs to the agent, the system prompt
> that encodes PNS debugging logic, and the conversation handler that routes PNS queries. You can wire
> an AI framework in weeks — getting the tools and reasoning right took five years of PNS domain
> experience."

**What the app does (30 sec):** On-call PNS engineers used to spend 15–25 minutes manually querying
4–5 systems (our config/template service, inventory service, Cassandra, promise-aggregation service) to
find why an offer wasn't getting 2-day delivery. The assistant takes the question in plain English,
figures out which systems to call, chains them, and returns a reasoned root cause in **8–12 seconds**.
Serves Mexico and Canada markets. Runs on an internal ~120B model on our Azure tenant (data stays in Walmart).

---

## 📖 Terminology (gloss these the first time you say them)

- **Tool / function-calling** (a function you expose to the LLM with a plain-English description — the model reads the description and decides when to call it, like handing someone a labeled phone directory)
- **System prompt** (the standing instructions given to the model before any user question — where I encoded the PNS debugging order)
- **LangChain4j** (the Java framework the team used to wire the model to the tools — I used its `@Tool` annotation, I didn't build the framework)
- **RAG** (retrieval-augmented generation — letting the model read indexed internal docs; team-built, I helped define what to index)
- **Agentic workflow** (breaking a task into steps the model executes by calling tools in sequence, reasoning between calls)

---

## Story AI-1 — Owning the PNS Domain Layer (proudest AI work / ownership / innovation)

**Use for:** recent AI work, innovation, end-to-end ownership, "tell me about an AI project," AI fluency.

**S —** PNS on-call engineers burned 15–25 minutes per incident manually querying 4–5 systems just to
find why an offer wasn't 2-day eligible. The team decided to build an AI assistant to collapse that.

**T —** I owned the part that required actually knowing the domain — the tool layer and the reasoning
logic — while the team built the AI plumbing.

**A —**
- Implemented **40+ tools** with LangChain4j's `@Tool` annotation, each wrapping a real internal PNS API —
  our config/template service (templates, carrier methods, zone charges, restrictions), our inventory
  service, the offer-replay/validation service, and direct Cassandra lookups.
- Wrote the **system prompt** encoding the actual debugging sequence: check template mapping first, then
  carrier restrictions, then zone, then inventory last — plus market defaults for Mexico and Canada.
- Built the **conversation handler** that routes PNS-domain queries to the agent with multi-market
  validation.

**R —** Investigation dropped from **15–25 minutes to 8–12 seconds**, returning root cause + a
recommended fix, not just raw data. The lesson: the AI was the easy part — the value was translating
years of domain intuition into rules a machine applies.

**🎯 The decision I made (state it explicitly):**
> "The key decision was the *ownership split*. I deliberately did **not** try to own the AI
> infrastructure — any strong backend engineer can wire LangChain4j. I owned the domain layer because
> that's where the app would live or die: an agent that calls the wrong API in the wrong order is
> confidently wrong, which for an on-call tool is worse than useless."

**⚠️ Likely pushbacks → how to answer:**

| Pushback | Your answer |
| --- | --- |
| "So you just wrote wrappers around APIs?" | "The wrapper is trivial — the hard part is the *description*. The LLM picks the tool purely from the description. `getCarrierData` vs `getCarrierMethodInfo` vs `getCarrierNetworkInfo` all sound alike; if the descriptions overlap, the model picks wrong. Writing descriptions precise enough to disambiguate required knowing the subtle difference between those APIs." |
| "How is this different from calling the APIs directly?" | "Calling directly needs the engineer to already know *which* APIs and *what order* — that's the manual 15-minute process. My layer moves that knowledge into the tools + prompt, so the engineer describes the problem and the agent does the investigation." |
| "Why an LLM at all — couldn't this be a script/decision tree?" | "A script handles one exact question shape. But engineers ask in free text, offers fail for many different reasons (not one fixed path), and they want a reasoned answer — not five raw API dumps to interpret at 2am. That's reasoning over live data = an agent. Where a step *is* deterministic, I kept it in the tool, not the LLM." |
| "How much of this did *you* actually build?" | Give the honest split verbatim (Section top). "Domain-agnostic plumbing = team. Domain layer = me. That split made sense because the plumbing is complex but generic; the domain layer needed five years of PNS." |
| "How do you know it didn't just get lucky on your demo?" | Go to Story AI-4 (validation against 20 real historical incidents). |

---

## Story AI-2 — Tool Disambiguation (hardest technical part / attention to detail)

**Use for:** hardest part, technical depth, iterative problem-solving.

**S —** We had multiple tools that were all "about carriers" — `getCarrierData`,
`getCarrierMethodInfo`, `getCarrierNetworkInfo`. Each serves a different debugging scenario.

**T —** Make the agent reliably pick the *right* one, because a wrong tool selection cascades into a
wrong root cause.

**A —**
- Rewrote tool descriptions from generic ("gets carrier info") to scenario-specific ("use this when you
  need to understand why a specific carrier method is blocking express eligibility for an offer").
- Ran **real PNS debugging scenarios** through the agent and checked which tool it actually called; when
  it picked wrong, I revised the description and retested. Iterative, not one-shot.
- Kept tools **small and focused** rather than merging into one big tool — the model performs better on
  narrow tools than on tools returning large mixed blobs.

**R —** The agent consistently picked the correct tool for a given question. The lesson: with LLM tools,
the description *is* the code — precision there matters more than the implementation behind it.

**🎯 The decision:** "Keep tools granular vs merge them. I chose granular — the trade-off is more tools
to maintain, but the model's selection accuracy is far higher with focused tools. For an on-call tool,
accuracy beats maintenance convenience."

**⚠️ Likely pushbacks → how to answer:**

| Pushback | Your answer |
| --- | --- |
| "That sounds like trial-and-error, not engineering." | "It was *measured* iteration against a fixed test set of real scenarios — same discipline as tuning any system against a benchmark. The alternative, guessing at descriptions and shipping, is what I was avoiding." |
| "How would you make this repeatable / less manual?" | This is the prevention answer → "I'd build a per-tool test harness with mocked API responses and assert tool-selection on a labeled scenario set in CI, so a description change that regresses selection fails the build. We tested end-to-end first, which made failures hard to isolate — that's the thing I'd do differently." (See AI-6.) |
| "What if two tools genuinely overlap?" | "Then they shouldn't be two tools — I'd merge or re-scope. Overlap in descriptions is a design smell that the tool boundaries are wrong." |

---

## Story AI-3 — Encoding Domain Logic in the System Prompt (domain → code)

**Use for:** turning expertise into a system, judgment, "teach a machine what you know."

**S —** Early agent versions did unnecessary/wrong-order tool calls — e.g. checking the inventory service
*before* carrier restrictions, even though inventory is rarely the eligibility blocker.

**T —** Encode the correct PNS reasoning order so the agent debugs the way an experienced engineer does.

**A —**
- Added explicit priority ordering to the prompt: template mapping → carrier restrictions → zone →
  inventory last.
- Added market defaults (e.g. Mexico postal-code default) because on-call queries often omit the postal
  code and tools that require it would otherwise fail.
- Each addition came from watching the agent get a *real* question wrong and knowing enough PNS to fix
  the reasoning.

**R —** The agent's investigation path matched how a senior engineer actually debugs. The lesson:
domain expertise became executable — the prompt is where five years of intuition turned into rules.

**🎯 The decision:** "Put the reasoning in the *system prompt* rather than hard-coding a fixed
decision tree in Java. Trade-off: the prompt is less deterministic than code, but it stays flexible as
the domain shifts and it keeps the reasoning readable and editable by domain people, not just the
original author."

**⚠️ Likely pushbacks → how to answer:**

| Pushback | Your answer |
| --- | --- |
| "Isn't prompt logic fragile vs real code?" | "For a deterministic sub-step, yes — and where correctness was non-negotiable I kept it in the tool, not the prompt. The prompt holds the *investigation strategy*, which genuinely benefits from the model's flexibility. It's a deliberate boundary, not laziness." |
| "How do you stop the model ignoring your prompt rules?" | "Validation set (AI-4). If it violated the order on a known case, I tightened the wording and, for the parts that mattered most, moved the guarantee into the tool itself so it couldn't be skipped." |
| "How do you version/change this safely?" | "Same as any behavior change — re-run the historical-incident set before and after a prompt change and diff the tool-call paths. A prompt edit is a behavioral change and gets the same regression check as code." |

---

## Story AI-4 — Validating the Agent / Earning Trust from Skeptical Experts (Trust + Customer Success)

**Use for:** "how did you know it was correct," earning trust, difficult/skeptical users, customer focus.
**This is your #1 story for Salesforce's Trust value in an AI context.**

**S —** The users were on-call PNS engineers — expert, skeptical users who would not trust a confident-
sounding AI answer just because it sounded confident.

**T —** Prove the agent was actually correct, not just plausible, before anyone would rely on it during
an incident.

**A —**
- Took **20 real past incidents** where we already knew the root cause from manual debugging.
- Ran those exact questions through the agent and checked: did it call the right tools in the right
  order, and did it reach the correct root cause?
- For each miss, traced the cause — usually a tool description or a system-prompt rule — and fixed it.
- Had domain experts review a weekly sample of responses for *reasoning soundness*, not just plausible-
  looking answers.

**R —** The agent was validated against cases the experts had personally debugged, which is what earned
their trust. The lesson: you earn trust from expert users by proving it on *their* hard cases, not by
selling the technology.

**🎯 The decision:** "Validate against *known-answer historical incidents* rather than synthetic
prompts or eyeballing demos. Trade-off: it's slower to assemble a real-incident set, but it's the only
evidence a skeptical expert accepts — and it doubles as a regression suite."

**⚠️ Likely pushbacks → how to answer:**

| Pushback | Your answer |
| --- | --- |
| "20 cases is a small sample." | "Agreed — it was the *validation* set that proved the reasoning, not a statistical guarantee. The right next step is turning it into a continuously growing regression set so every new incident becomes a test case. I'd frame 20 as the floor, not the ceiling." |
| "What about hallucination — the AI making things up?" | Go to Story AI-5 (structured error returns so the agent says 'the config service timed out' instead of inventing an answer). |
| "How do you keep it correct as PNS changes?" | "The historical-incident set becomes a CI regression gate; a PNS change that breaks a known case fails the check. Correctness isn't a launch event, it's a standing test." |
| "Did engineers actually adopt it?" | Be honest about what you know. Say the *measured* win (15–25 min → 8–12 s) and, if you don't have hard adoption numbers, say so: "The primary measure we tracked was investigation time; I don't want to quote an adoption number I can't stand behind." |

---

## Story AI-5 — Designing for Failure (hallucination prevention / production mindset)

**Use for:** failure modes, reliability, "how did you make it production-safe," senior judgment.

**S —** If a tool threw an exception when a PNS API was down, the LLM would get confused and sometimes
*hallucinate* an answer rather than admit the failure — dangerous for an on-call tool.

**T —** Make the agent fail honestly instead of confidently making something up.

**A —**
- Made tools return **structured error strings** — e.g. "API unavailable: config service timeout after 45s" —
  instead of throwing.
- That let the agent say "I tried to get carrier method info but the config service isn't responding" rather
  than inventing a root cause.

**R —** The agent degraded honestly under downstream failure. The lesson: for an AI tool, a wrong-but-
confident answer is worse than "I couldn't check" — the failure path had to be designed as carefully as
the happy path.

**🎯 The decision:** "Return structured errors to the model vs throw exceptions. Trade-off: the tool
code is slightly less 'clean' than letting exceptions propagate, but it removes an entire class of
hallucination. For a tool experts rely on during incidents, honesty under failure was the priority."

**⚠️ Likely pushbacks → how to answer:**

| Pushback | Your answer |
| --- | --- |
| "How do you *know* it stops hallucination and doesn't just reword it?" | "Because the model now has a concrete fact to report — the error string — instead of a void it fills. I verified it on induced-failure cases: kill the API, confirm the agent reports the outage rather than a fake root cause." |
| "What if the API is slow, not down?" | "Timeout with a structured 'timed out after Ns' message — same principle, the model reports the degradation instead of waiting or guessing." |
| "Isn't this just error handling?" | "It's error handling *designed for an LLM consumer* — the failure has to be legible to a model, not just logged for a human. That framing is the difference." |

---

## Story AI-6 — What I'd Do Differently (the senior-signal answer)

**Never say "nothing."** Two honest ones:

1. **Per-tool test harness earlier.** "We tested tools end-to-end through the full agent, which made it
   hard to isolate whether a wrong answer came from a tool returning wrong data or the LLM
   misinterpreting correct data. A unit-test suite per tool with mocked API responses would have
   sped up the validation loop significantly."
2. **Structured logging of tool-selection reasoning.** "When the model picked the wrong tool, we saw it
   in the output but couldn't easily trace which part of the prompt drove the choice. Logging the
   reasoning chain would have made iteration much faster."

Both double as **prevention answers** — they're the systemic fixes that make the whole class of problem
easier next time.

---

## 🌉 Mapping to the Salesforce Signup JD (AI Fluency = core expectation)

| JD line | Your evidence |
| --- | --- |
| "Use AI tools across the full SDLC" | You built agentic tooling *and* you author governed AI knowledge bases (this repo, with AGENTS.md standards) |
| "Think agentic — decompose problems into agent-executable workflows" | The tool + system-prompt design *is* exactly this: decomposing PNS debugging into tool calls the agent sequences |
| "Build AI-powered skills/automations and share them broadly" | The assistant collapsed a team-wide manual process; the domain layer is the reusable asset |
| "AI amplifies every engineer's impact" | 15–25 min → 8–12 s per incident is the concrete amplification |

**One-liner to drop in the HM round when AI comes up:**
> "I've done this for real, not just used a copilot. I built the domain layer of an AI agent that
> collapsed a 15–20 minute multi-system on-call investigation into ~10 seconds — 40+ tools plus a system
> prompt encoding the debugging logic, validated against real historical incidents. That's the same
> agentic thinking your JD asks for, applied to a live production domain."

---

## 5 — Confidentiality Guardrails (applies to every answer above)

```
✓ SAFE                                     ✗ NEVER SAY
────────────────────────────────────────────────────────────────
"our promise & sourcing domain"            internal codenames / page IDs
"an enterprise LLM on our cloud tenant"    confidential model/vendor contract terms
"~700K rpm, sub-100ms p95"                 confidential GMV / revenue figures
"40+ tools over our internal APIs"         internal endpoint URLs / secrets
"a large enterprise market"                specific seller / carrier / BU IDs
```

Two rules that cover everything:
1. If it needs Walmart SSO to see, describe the *concept*, not the identifier.
2. Never throw a teammate or "the old code" under the bus — "a pattern in the codebase," never "someone's bad code."

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| Aug 26, 2026 | **File created.** Six AI PNS Assistant stories (AI-1..AI-6) in STAR form with explicit decisions and scripted pushbacks, drawn from `aiPnSBackend/prep` files 01, 03, 15. Honest ownership split enforced (domain layer = mine, infra = team). Salesforce JD AI-fluency mapping added. Confidentiality guardrails carried over. |
| Aug 28, 2026 | **Scrubbed to match the deep-dive** — internal codenames (DCC/Wakanda/Unified Promise/PNO Ingestor) → concepts; internal model naming → "internal ~120B model on our Azure tenant"; `getMcse…` tool name genericized. Added a Scrub-rule note. Added an AI-1 pushback: "why an LLM, not a script?" |
