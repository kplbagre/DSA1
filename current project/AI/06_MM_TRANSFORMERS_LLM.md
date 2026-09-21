# 06 — Mental Model: Transformers & LLMs
### How GPT actually works — visuals first, labels second

> **Read time:** 35 min
> **Companion video:** 3Blue1Brown "But what is a GPT?" (you already watched it)
> **Goal:** Lock the mental model so you can explain it in your own words.

---

## Mental Model #1 — The Autocomplete That Grew Up

Forget "AI" and "intelligence." Start here:

```
YOUR PHONE KEYBOARD AUTOCOMPLETE:
  You type:  "I will be there"
  Phone suggests: "soon" / "at 5" / "tomorrow"

  The phone learned from YOUR past messages.
  It predicts the most likely next word.

GPT IS THE SAME IDEA — scaled to the entire internet:
  Trained on: books, articles, code, Wikipedia, StackOverflow...
              (trillions of words)

  You type:  "Why is offer 12345 not"
  GPT predicts: "eligible" → then "for" → then "2-day" → then "delivery"...

  Each word is predicted ONE AT A TIME.
  Each prediction uses everything written before it.
```

**That's it. The "magic" is just very good prediction at enormous scale.**

---

## Mental Model #2 — What is a Token?

```
NOT a word. A word-PIECE.

"carrier"        → 1 token
"carrier method" → 2 tokens
"getCarrierMethodInfo" → 4-5 tokens
"fulfillment"    → 2 tokens  (ful + fillment)
"a"              → 1 token
"."              → 1 token

RULE OF THUMB:
  ~750 words ≈ 1,000 tokens

WHY DOES IT MATTER?
┌───────────────────────────────────────────────┐
│  The LLM has a MAXIMUM it can process at once │
│  This is called the CONTEXT WINDOW            │
│                                               │
│  Our app: 8,192 tokens max                    │
│                                               │
│  Everything the LLM "sees" must fit:          │
│  ├── System prompt        ~800 tokens         │
│  ├── Tool definitions     ~2,000 tokens       │
│  ├── RAG documents        ~1,500 tokens       │
│  ├── Conversation history ~2,000 tokens       │
│  └── User message         ~200 tokens         │
│                           ─────────────────   │
│                           ~6,500 tokens used  │
│                           ~1,700 remaining    │
└───────────────────────────────────────────────┘

This is why conversation history is capped at 30 messages.
Beyond that → doesn't fit in the window.
```

---

## Mental Model #3 — The Context Window as a Spotlight

```
Imagine the LLM has a spotlight. It can only see what's inside it.
Everything outside the spotlight doesn't exist to the LLM.

CONTEXT WINDOW = The size of the spotlight

  ┌─────────────────────────────────────────────────────────┐
  │                                                         │
  │  📜 System Prompt                                       │
  │  "You are a PNS assistant. When debugging eligibility,  │
  │   check template first, then carrier..."                │
  │                                                         │
  │  📄 Document from Milvus (RAG)                         │
  │  "...carrier method CM789 supports Standard only..."    │
  │                                                         │
  │  💬 Message 28: "Why is offer 99 not eligible?"         │
  │  🤖 Response 28: "Offer 99 has template T5..."          │
  │  💬 Message 29: "What about offer 12345?"               │
  │                                                         │
  │  🔧 Tool definitions: getTemplateInfo, getCarrierInfo...│
  │                                                         │
  │  💬 YOU NOW: "Is CM789 causing the issue?"              │
  │                                                         │
  └─────────────────────────────────────────────────────────┘
                    ▲ LLM sees ALL of this
                    ▲ and predicts the response

  Message 1 from 3 days ago? OUTSIDE the spotlight. Forgotten.
```

---

## Mental Model #4 — Temperature = Creativity Dial

```
TEMPERATURE DIAL:

  0.0         0.2          0.5          1.0          2.0
   │           │            │            │             │
   ▼           ▼            ▼            ▼             ▼
ROBOT       OUR APP     BALANCED     CREATIVE      CHAOTIC

"The       "The max    "The max     "The max     "The max
 max SLA    SLA is      SLA is       SLA is       SLS is
 is         3 days."    around       probably     maybe
 3 days."               3 days."     3 or so."    three?"


AT 0: Always picks the single most probable next token.
      Same input → always same output.

AT 0.2 (our app): Nearly always picks the most probable.
      Tiny variation allowed.
      Correct for debugging tools — you want "3 days" not "around 3"

AT 1.0: Introduces real randomness.
      Good for creative writing, bad for PNS debugging.

INTERVIEW ANSWER:
"We use 0.2 — near-deterministic. An on-call engineer asking about
 carrier SLA needs a precise, consistent answer. Creative variation
 is a bug in a debugging tool, not a feature."
```

---

## Mental Model #5 — How the LLM "Reads" a Long Prompt

This is what the 3Blue1Brown video showed with the attention mechanism.

```
PROBLEM: How does the LLM connect related words far apart?

Example prompt:
"...the carrier method CM789, which was configured last quarter
 for Mexico Zone 4 deliveries, does not support express..."

The word "express" is 15 words after "CM789".
How does the LLM know "express" relates to "CM789"?

ATTENTION MECHANISM:
  When predicting what comes after "express", the LLM assigns
  ATTENTION WEIGHTS to every previous word:

  "the"      → 0.01 (low attention — not relevant)
  "carrier"  → 0.15 (medium — carrier is related)
  "CM789"    → 0.45 (HIGH — CM789 is what we're describing)
  "Mexico"   → 0.20 (medium — location matters)
  "express"  → 0.10 (itself)

  It "looks back" at CM789 with high attention, pulls that
  context forward, and generates "not support express delivery."

FOR YOU: You don't need to explain HOW attention works.
         You need to know WHY it matters:

  → The LLM can connect "offer 12345" to information about
    "12345" mentioned 2,000 tokens earlier in the prompt
  → This is how it links your question to the right tool result
  → This is why longer context = more powerful reasoning
```

---

## Mental Model #6 — What "Training" Means

```
BEFORE training: Random weights. LLM outputs gibberish.

DURING training (simplified):
  Show the model: "The carrier method supports ___"
  Model guesses:  "banana"
  Correct answer: "standard"
  → Adjust weights slightly toward "standard"

  Repeat this for TRILLIONS of examples.
  Weights slowly become good at predicting real language.

AFTER training: Weights are FROZEN. The model doesn't learn anymore.

THIS IS WHY:
  → gpt-oss-120b doesn't know about DCC changes made last week
  → It doesn't know what Wakanda returns today
  → It has no idea what CM789's current SLA is
  → Its knowledge has a training cutoff date

THIS IS WHY WE NEED TOOLS:
  → Tools call the LIVE systems in real time
  → The LLM reasons, the tools provide current data
  → Perfect division of labour
```

---

## The 6 Numbers to Know Cold

```
┌─────────────────────────────────────────────────────┐
│  gpt-oss-120b  → The model name (Azure OpenAI)      │
│  8,192         → Context window (tokens)            │
│  0.2           → Temperature (near-deterministic)   │
│  120 seconds   → LLM call timeout                   │
│  120B          → Parameter count (why it's "smart") │
│  1 at a time   → Tokens generated (sequential)      │
└─────────────────────────────────────────────────────┘
```

---

## Interview-Ready Summary

> "gpt-oss-120b is a large language model — it predicts the next token based
> on everything in its context window. The context window is 8,192 tokens —
> that's the LLM's working memory. Everything we send: system prompt, tool
> definitions, conversation history, RAG documents, and the user's message —
> must fit in those 8,192 tokens. We use temperature 0.2 — near-deterministic —
> because PNS debugging answers need to be precise and consistent, not creative.
> The LLM itself has a training cutoff and knows nothing about our live DCC or
> Wakanda state — that's exactly why we expose those as tools that the agent
> calls at runtime."

---

**Next: `07_MM_RAG_AND_AGENTS.md`**
