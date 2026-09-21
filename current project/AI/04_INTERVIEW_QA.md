# 04 — Interview Q&A
### Every question you will face — with approach, what to say, and what not to say

> **Reading time:** Core = 75 min to internalize | Deep Dive = 45 min
> **How to use this file:** Read the APPROACH before the answer.
> The approach tells you HOW to think. The answer tells you WHAT to say.
> Practise each answer out loud. Time yourself — 45-90 seconds per answer.

---

## HOW TO USE THIS FILE

Each question follows this format:

```
❓ The question
─────────────────
💡 APPROACH     → How to frame your thinking before speaking
✅ SAY          → The actual answer (2-4 sentences for most)
⛔ DON'T SAY    → Common mistakes
🎯 SIGNAL       → What this answer tells the interviewer about you
```

---

## CATEGORY 1 — About the Application (3 Questions)

---

**Q1: "Tell me about the most interesting project you've worked on recently."**
```
💡 APPROACH
   This is your opener. Use it to land the project pitch cleanly.
   Structure: Problem → Solution → Your Role → Scale/Impact.
   Do NOT start with technology. Start with the human problem.

✅ SAY
   "The most interesting one recently was an AI assistant we built for
   Walmart's Promise and Shipping domain. The problem it solves is real —
   on-call engineers debugging offer eligibility issues have to manually
   query 4 or 5 different systems: DCC for carrier configs, Wakanda for
   inventory, Cassandra for raw data, Unified Promise for promise data.
   A 2am incident investigation can take 15-20 minutes of clicking around.

   We built an AI agent that accepts natural language questions and
   automatically queries the right systems, chains the results, and gives
   a reasoned answer in 8-12 seconds. I owned the PNS domain layer —
   the 40-plus tool implementations and the system prompt that encodes
   PNS business logic. The team built the AI infrastructure around it."

⛔ DON'T SAY
   "We built a chatbot using ChatGPT."
   Starting with tech before the problem.
   "It uses LangChain4j and Milvus and..." (too fast into acronyms)

🎯 SIGNAL
   Problem-first thinking. Ability to explain complex AI work simply.
```

---

**Q2: "What problem does this application actually solve?"**
```
💡 APPROACH
   Be very specific. Use the 15-20 minute manual debugging story.
   Quantify the time saved. Connect to real on-call pain.

✅ SAY
   "It solves the multi-system manual debugging problem in PNS on-call.
   When an offer fails eligibility — say it's not getting 2-day delivery —
   an engineer has to check DCC for the template config, then another DCC
   call for carrier method, then Cassandra for raw data, then Wakanda for
   inventory. Each system has its own interface, its own query format.
   That's 4 context switches and 15-20 minutes of cognitive work at 2am.

   The app collapses that into a single question typed in plain English.
   The AI knows which systems to query for which type of question — because
   that logic is encoded in the tools and the system prompt. Same
   investigation, 8-12 seconds."

⛔ DON'T SAY
   Vague answers like "it helps engineers debug issues faster."
   Not quantifying the before/after.

🎯 SIGNAL
   Deep understanding of the real user problem, not just the technology.
```

---

**Q3: "Who are the users and how do they use it?"**
```
💡 APPROACH
   Be specific about user types. Mention both the chat use case and
   the admin/tools explorer — shows you know the full product.

✅ SAY
   "Two main user groups. First, on-call and operations engineers in
   Mexico and Canada — they use the chat interface to debug live incidents.
   A typical session is: ask about an offer, follow up with more specific
   questions, use the session history to reference earlier findings.
   Second, the admin team — they use the admin panel to manage access,
   view analytics on what questions are being asked most, and see feedback
   on AI responses.

   There's also a MCP Tools Explorer — engineers can manually invoke any
   of the 40-plus tools without going through the AI. Useful for verifying
   what a specific API returns during debugging."

⛔ DON'T SAY
   "Engineers use it to chat with AI."

🎯 SIGNAL
   Product depth. You understand the full surface area, not just one feature.
```

---

## CATEGORY 2 — Architecture & Design (6 Questions)

---

**Q4: "Walk me through the architecture."**
```
💡 APPROACH
   Use the 6-zone structure from File 02. Start from the user (top),
   go down to data (bottom). Name each zone before explaining it.
   Keep it to 90 seconds — offer to go deeper on any part.

✅ SAY
   "There are six layers. At the top, a React frontend — the chat UI,
   market and domain selectors, session history. The user's message goes
   over SSE — Server-Sent Events — which keeps the connection open and
   streams each step back in real time.

   Below that, Spring Boot handles authentication with PingFederate SSO,
   validates JWT tokens, and routes to the service layer.

   The orchestration layer — AdvancedConversationalFlowService — runs a
   3-step transactional flow: save the user message to Cassandra first,
   process the AI response in memory, save the final answer to Cassandra.
   It detects the domain — PNS or Confluence — and routes to the right handler.

   The AI agent layer is LangChain4j — it builds the full prompt from the
   system instructions, Milvus RAG documents, conversation history, and
   tool definitions, and manages the loop of calling gpt-oss-120b, executing
   tools, and repeating until a final answer.

   Below that, the tools layer — 40-plus tool implementations wrapping
   real PNS APIs. And at the base, Cassandra for conversation storage
   and Milvus for document vector storage."

⛔ DON'T SAY
   Starting with tech stack (Java, Spring Boot) before structure.
   Mixing up layers.

🎯 SIGNAL
   Ability to communicate architecture clearly. Systems thinking.
```

---

**Q5: "Why did you choose Java and Spring Boot and not Python?"**
```
💡 APPROACH
   3-part answer: constraint, fit, trade-off. DO acknowledge Python is
   the dominant AI ecosystem. That honesty is senior.

✅ SAY
   "Honest answer — Python has a richer AI ecosystem. LangChain in Python
   is more mature than LangChain4j. But the PNS platform at Walmart runs
   on Java and Spring Boot — the team owns Java expertise, the deployment
   pipelines are Java-optimised, the monitoring setup is Java-native.
   Introducing Python would have meant a second language, second deployment
   pipeline, second set of operational concerns.

   LangChain4j — the Java port — gives us the core AI orchestration
   capabilities we needed. The trade-off we accepted is a slightly less
   mature ecosystem. What we gained is operational simplicity — one
   language across the team."

⛔ DON'T SAY
   "Python is not good." → Wrong. Python is dominant in AI.
   "Java is better for AI." → Not true.

🎯 SIGNAL
   Honest trade-off thinking. Infrastructure pragmatism over tech preference.
```

---

**Q6: "What is the transactional memory pattern and why did you use it?"**
```
💡 APPROACH
   This is a design pattern question. Explain the problem first,
   then the solution, then connect to a database analogy they'll know.

✅ SAY
   "The problem: LLM calls can take up to 120 seconds. If the server
   crashes at second 90, without the transactional pattern you'd lose
   both the user's question and any partial progress — the conversation
   has a gap.

   We use a 3-step approach: save the user message to Cassandra immediately
   on request receipt — before any AI processing. Then run the AI agent
   in memory — tool results are not persisted during processing, only kept
   in the thread's working memory. Then save the final AI response to
   Cassandra on success.

   It is essentially the write-ahead log pattern from databases — you
   write the intent before executing it, so a crash mid-execution leaves
   a recoverable state. Here, the 'intent' is the user's message, and the
   'execution' is the AI response."

⛔ DON'T SAY
   Not connecting it to a known pattern. Saying "we just save early."

🎯 SIGNAL
   Connects AI system design to classical distributed systems patterns.
   Shows depth.
```

---

**Q7: "How does multi-tenancy work in this system?"**
```
💡 APPROACH
   Explain the isolation mechanism — thread-local context, composite
   memory key, separate Milvus collections. Keep it concrete.

✅ SAY
   "We support Mexico and Canada from one deployment. Isolation works at
   three levels.

   First, the tenant context — when a request comes in, we detect the
   market from the JWT token and HTTP header, and store it in a ThreadLocal
   variable. ThreadLocal means each concurrent request thread has its own
   isolated copy of the market context — requests don't share state.

   Second, the memory key — Cassandra conversations are stored under a
   composite key: market underscore domain colon tenantCode colon sessionId.
   So Mexico PNS and Canada PNS sessions never share memory, even for the
   same user.

   Third, Milvus collections — each tenant has its own vector collection.
   Mexico's indexed documents are separate from Canada's. A RAG query in a
   Mexico session only searches Mexico's Milvus collection."

⛔ DON'T SAY
   "We just check the market and route." → Too vague.

🎯 SIGNAL
   Understanding of tenant isolation patterns in distributed systems.
```

---

**Q8: "What is RAG and how is it implemented here?"**
```
💡 APPROACH
   Problem → Solution → Specifics. Use the context window constraint
   as the motivating problem. Give the actual numbers.

✅ SAY
   "RAG — Retrieval-Augmented Generation — solves the knowledge problem.
   The LLM was trained on public internet data and knows nothing about
   our internal runbooks or PNS process docs. The context window is 8,192
   tokens — we can't put thousands of pages in every prompt.

   So we pre-process all internal documents: split into 1,200-character
   chunks, convert each chunk to a 1536-dimensional vector using Azure
   OpenAI's text-embedding-3-large model, store vectors in Milvus.

   On every user query, we embed the question using the same model, query
   Milvus for the 5 most similar chunks using cosine similarity, and add
   those chunks to the prompt before calling gpt-oss-120b. The LLM now
   has targeted, relevant context — not everything, just what's needed
   for this specific question."

⛔ DON'T SAY
   "It searches documents." → Not wrong but too vague.
   Not mentioning the embedding model or vector dimensions.

🎯 SIGNAL
   Specific technical understanding. Knows the actual implementation, not
   just the concept.
```

---

**Q9: "How does the frontend know what the AI is doing in real time?"**
```
💡 APPROACH
   SSE streaming. Walk through the event types. Connect to the
   AgentThinkingPanel the user sees.

✅ SAY
   "The frontend opens an SSE connection — Server-Sent Events — which is
   a persistent one-way HTTP stream. The backend pushes events as each
   pipeline stage completes.

   When the classifier determines the intent, it sends a 'classifier' event
   with the detected intent and complexity. When the planner determines
   which tools to call, it sends a 'planner' event. As each tool executes,
   'tool_executor' events come with tool name and status — running or done.
   Finally, a 'synthesizer' event carries the actual answer, and a 'done'
   event sends the metadata: total tokens, duration, number of tool calls.

   The frontend's AgentThinkingPanel component subscribes to these events
   and updates the progress view live. The user sees exactly which PNS
   system the AI is querying and can follow the investigation in real time.
   This was important for trust — if the AI takes 10 seconds, you want to
   know it's working, not hung."

⛔ DON'T SAY
   "We use WebSocket for the chat." → Wrong. SSE for chat, WebSocket for MCP.

🎯 SIGNAL
   Knows the system end to end including the frontend integration.
```

---

## CATEGORY 3 — AI and LLM Concepts (5 Questions)

---

**Q10: "What is an AI agent and how is it different from just calling an LLM?"**
```
💡 APPROACH
   Start with what a plain LLM is (text in, text out). Then add what
   an agent adds. Use the intern analogy if it helps you.

✅ SAY
   "A plain LLM is text-in, text-out — you give it a question, it gives
   you an answer based on its training data. It can reason, but it cannot
   take actions or access live data.

   An agent wraps the LLM with three additions: tools — functions it can
   call to access real systems; memory — conversation history so it knows
   context; and a loop — it keeps calling the LLM, executing tools, and
   feeding results back until it has enough data to give a final answer.

   In our case, a plain LLM would hallucinate carrier method details because
   it doesn't know our DCC configuration. The agent calls DCC as a tool,
   gets real data, and reasons over actual production values."

⛔ DON'T SAY
   "An agent is smarter than an LLM." → Not accurate.

🎯 SIGNAL
   Clear conceptual distinction. Shows you understand AI fundamentals.
```

---

**Q11: "What is temperature and why is it set to 0.2 in this app?"**
```
💡 APPROACH
   Short and specific. Explain what temperature does, then why low
   temperature is right for this use case.

✅ SAY
   "Temperature controls how random the LLM's token selection is. At 0,
   it always picks the single most probable next token — fully deterministic.
   At 1, it introduces randomness — useful for creative tasks.

   We use 0.2 — nearly deterministic. When an on-call engineer asks why
   an offer is not eligible, the answer needs to be precise and consistent.
   If the carrier method maxSLA is 3 days, the answer should be '3 days'
   every time — not '3 days' sometimes and 'around 3 days' others. Creative
   variation is a bug in a debugging tool, not a feature."

⛔ DON'T SAY
   "Higher temperature is better." → Context-dependent.

🎯 SIGNAL
   Understands LLM configuration decisions in context. Not just knowing
   the parameter — knowing WHY it's set as it is.
```

---

**Q12: "What is a token and why does it matter for this system?"**
```
💡 APPROACH
   Explain token briefly, then connect to the real constraints it
   creates in this app (context window, cost).

✅ SAY
   "A token is roughly a word or word-piece — 'carrier' is one token,
   'carrier_method' might be two. The LLM processes text in tokens, not
   characters.

   It matters for two reasons. First, the context window — gpt-oss-120b
   can process 8,192 tokens at once. Every prompt we send includes the
   system prompt, conversation history, RAG documents, and 40-plus tool
   definitions. We have to fit all of that within 8,192 tokens. Memory
   is capped at 30 messages partly for this reason.

   Second, cost — Azure OpenAI charges per token. A complex query with
   10 tool calls might use 15,000 tokens across all the LLM calls in that
   loop. We wrap the model with a TokenCountingChatModelWrapper that logs
   every token used, and the analytics dashboard shows cost per query,
   per market, per day."

⛔ DON'T SAY
   "A token is a word." → Close but not precise.

🎯 SIGNAL
   Understands real production constraints — cost and context limits.
```

---

**Q13: "How does the LLM decide which tool to call?"**
```
💡 APPROACH
   This is directly about your work. Be specific. The LLM reads
   descriptions. Quality of descriptions = quality of tool selection.

✅ SAY
   "Before the first LLM call, LangChain4j includes all 40-plus tool
   definitions in the prompt — tool name, description, and parameter
   schema. The LLM reads these and matches them against the user's question.

   The quality of this matching depends entirely on how the tool descriptions
   are written. If getCarrierMethodInfo says 'gets carrier method information',
   the LLM doesn't know when to use it versus getCarrierData or getMcseCarrierInfo.
   If it says 'use this to understand the SLA, zone coverage, and express
   delivery support for a specific carrier method — primary tool for
   eligibility failure investigation involving carriers', the LLM picks it
   correctly for carrier-related eligibility questions.

   Writing these descriptions was part of my contribution — it requires
   knowing what each PNS API returns and when an engineer would actually
   need that data."

⛔ DON'T SAY
   "The LLM is smart and figures it out." → Too vague.

🎯 SIGNAL
   Direct ownership. Shows you understand the system at implementation depth.
```

---

**Q14: "What is the difference between embeddings and the LLM itself?"**
```
💡 APPROACH
   Two different models for two different jobs. Be clear about that
   distinction. Both are neural networks but they serve different purposes.

✅ SAY
   "Two different models, two different jobs.

   The LLM — gpt-oss-120b — is a text generation model. You give it a
   prompt, it generates text back. It reasons, explains, synthesises.

   The embedding model — text-embedding-3-large — is a text representation
   model. You give it text, it gives you a vector of 1536 numbers that
   represents the meaning of that text. It doesn't generate — it converts.

   We use the embedding model for RAG: convert documents to vectors offline,
   convert user queries to vectors at runtime, compare them in Milvus to find
   similar documents. We use the LLM for the actual reasoning — reading the
   retrieved documents and generating the answer.

   Think of it as: the embedding model organises the library by meaning,
   the LLM reads the relevant books and writes the answer."

⛔ DON'T SAY
   "They're both the same kind of AI." → Conceptually misleading.

🎯 SIGNAL
   Conceptual clarity. Knows the AI architecture at component level.
```

---

## CATEGORY 4 — Your Contribution (5 Questions)

---

**Q15: "What was the most technically challenging part of your contribution?"**
```
💡 APPROACH
   Pick ONE thing. Be specific. Don't say "everything was hard."
   The disambiguation problem is the best answer here — it is real,
   technical, and uniquely PNS.

✅ SAY
   "The hardest part was disambiguating tool descriptions for carrier-related
   tools. We have three of them: getCarrierData, getCarrierMethodInfo, and
   getMcseCarrierInfo. All three relate to carrier configuration but serve
   different debugging purposes. If their descriptions overlap, the LLM picks
   randomly — and a wrong tool call cascades into a wrong answer.

   I had to write descriptions precise enough that the LLM consistently picks
   the right one. The test: run a set of representative questions through the
   agent and check which tool it calls. When it called the wrong carrier tool,
   I revised the description — usually making it more specific about the use
   case — and retested. It took multiple iterations to get stable, consistent
   tool selection across the full range of carrier-related questions."

⛔ DON'T SAY
   "Setting up the environment." → Not technical.
   "Understanding LangChain4j." → Learning curve, not contribution.

🎯 SIGNAL
   Real technical challenge. Iterative approach. Domain-specific problem.
```

---

**Q16: "How did you handle errors in tool execution?"**
```
💡 APPROACH
   This shows you thought about failure modes — a senior signal.
   Talk about what happens when a PNS API is down or slow.

✅ SAY
   "Tools are configured with a 45-second timeout and 3 retry attempts.
   If a tool still fails after retries, the tool returns a structured
   error string rather than throwing an exception.

   That was a deliberate design decision. If the tool throws an exception,
   LangChain4j propagates it and the LLM has no information about what
   happened — it might hallucinate an answer. If the tool returns
   'DCC API unavailable: timeout after 45s', the LLM can include that
   in its response: 'I tried to retrieve the carrier method configuration
   but DCC is not responding. The carrier data could not be verified.'

   That's a much better outcome for an on-call engineer — they know which
   system is down and that the AI answer is incomplete because of it."

⛔ DON'T SAY
   "We handle errors with try-catch." → Too generic.

🎯 SIGNAL
   Thoughtful error design. Understands LLM behaviour under failure.
```

---

**Q17: "How did you approach writing the system prompt?"**
```
💡 APPROACH
   Be honest that it was iterative. Show the PNS logic you encoded.
   This is your deepest contribution — be specific.

✅ SAY
   "It was iterative — not a one-time design. The first version had the
   right tool names but no guidance on order or priority. The AI would
   call tools in an unpredictable sequence — sometimes checking Wakanda
   inventory first, when inventory is rarely the eligibility blocker.

   I added explicit tool-call ordering rules: for eligibility failures,
   check template mapping first, then carrier restrictions, then zone
   charges, then TNT, then inventory as a last resort. That mirrors how
   an experienced PNS engineer actually debugs — we go from the most
   common blockers to the least common.

   I also added market-specific defaults. Mexico's postal code default
   — 06600 — means the agent can call tools requiring a postal code even
   when the user doesn't specify one. Without it, the agent would ask the
   user for a postal code mid-conversation, which breaks the flow.

   Each addition came from watching the agent give a wrong or incomplete
   answer and knowing enough about PNS to fix the reasoning."

⛔ DON'T SAY
   "I wrote instructions and it worked." → Not believable.

🎯 SIGNAL
   Iterative design process. Domain knowledge applied to AI reasoning.
```

---

**Q18: "How much of this application did you build yourself?"**
```
💡 APPROACH
   Use the prepared honest answer from File 03. Don't be defensive.
   This tests honesty and self-awareness, not completeness.

✅ SAY
   "This was a team effort. The AI infrastructure — LangChain4j wiring,
   Cassandra memory management, Milvus setup, WebSocket protocol, the
   Spring Boot framework — was built collaboratively. My ownership was the
   PNS domain layer: the 40-plus tool implementations, the system prompt
   with PNS business logic, and the PNS conversation handler.

   That split made sense because the AI plumbing is technically complex
   but domain-agnostic — a good backend engineer can wire LangChain4j
   without knowing anything about PNS. The domain layer required someone
   who actually knew when to call DCC versus Wakanda for a given debugging
   question. That's where 5 years on MCSE made the difference."

⛔ DON'T SAY
   "I built most of it." → If not true, it will unravel.
   "I just helped a bit." → Undersells real contribution.

🎯 SIGNAL
   Honesty. Team orientation. Ability to articulate specific ownership.
```

---

**Q19: "What would you do differently if you rebuilt this?"**
```
💡 APPROACH
   Have 2-3 specific, honest answers. This is a senior signal question —
   the correct answer is NEVER "nothing." Pick things you actually would change.

✅ SAY
   "Two things I'd change.

   First, a dedicated tool testing harness. We validated tools end-to-end
   — through the full agent — which made it hard to isolate whether a
   wrong answer came from a tool returning incorrect data or the LLM
   misinterpreting correct data. I'd build a unit test suite for each tool
   with mocked API responses, so tool correctness is validated independently
   of the agent's reasoning.

   Second, structured observability for tool selection. Right now if the
   LLM picks the wrong tool, we see the wrong answer in the output but can't
   easily trace WHY that tool was chosen. I'd add structured logging of the
   LLM's reasoning chain — which tool description was matched, with what
   confidence — so we can iterate on descriptions much faster."

⛔ DON'T SAY
   "Nothing, it's well designed." → Not credible. Not senior.
   "We should have used Python." → Opens a rabbit hole.

🎯 SIGNAL
   Self-critical thinking. Senior engineering judgement. You understand
   what production-grade looks like versus what you shipped.
```

---

## CATEGORY 5 — Trade-offs and Decisions (5 Questions)

---

**Q20: "Why Azure OpenAI and not Anthropic Claude or direct OpenAI?"**
```
💡 APPROACH
   Data governance is the real answer. Be direct about it.

✅ SAY
   "Data governance. Walmart has an enterprise agreement with Microsoft
   Azure, and Azure OpenAI runs the same models but within Azure's private
   infrastructure — query data doesn't leave Walmart's Azure tenant.
   Sending PNS operational data to openai.com or anthropic.com would
   require additional data handling agreements and is not standard for
   production internal tooling at Walmart.

   Azure OpenAI gives us the same gpt-oss-120b model with the same
   capabilities, but within the security boundary we needed. The frontend
   also supports Claude Sonnet via model selector — but through the same
   Azure gateway."

⛔ DON'T SAY
   "Azure OpenAI is better." → The quality is the same model.

🎯 SIGNAL
   Enterprise security awareness. Not naive about data handling.
```

---

**Q21: "Why Milvus for the vector database and not Pinecone or pgvector?"**
```
💡 APPROACH
   Same data governance logic + specific pgvector limitation.
   Show you evaluated the options.

✅ SAY
   "Pinecone is fully managed — you send your vectors to Pinecone's cloud.
   Same data residency concern as direct OpenAI — vectors of PNS operational
   content cannot go to an external cloud. Self-hosted was the requirement.

   Among self-hosted options: pgvector as a PostgreSQL extension was
   considered — appealing because it's one less database. But pgvector's
   performance degrades significantly beyond a few million vectors because
   PostgreSQL's general-purpose architecture isn't optimised for
   high-dimensional similarity search. Milvus uses specialised indexes
   — IVF_FLAT in our case — that maintain performance at scale.

   Trade-off: Milvus adds operational overhead — another database to run
   and maintain. We accepted that for the performance and data residency."

⛔ DON'T SAY
   "Milvus is the best." → There is no universal best.

🎯 SIGNAL
   Evaluated multiple options. Data residency awareness. Trade-off thinking.
```

---

**Q22: "Why not use a single large prompt instead of tools?"**
```
💡 APPROACH
   Static vs dynamic data. The prompt can't hold live PNS state.

✅ SAY
   "A static prompt can only contain data you knew at prompt-writing time.
   PNS data is live — carrier method configurations change, distributor
   mappings get updated, inventory fluctuates in real time. There is no
   prompt we could write that contains the current state of DCC or Wakanda.

   Tools solve this by calling the real APIs at query time. When an engineer
   asks about an offer right now, the tool fetches what DCC says right now —
   not a cached or static approximation. For a debugging tool, stale data
   is worse than no data because it leads to wrong conclusions."

⛔ DON'T SAY
   "Prompts have token limits." → True, but not the core reason here.

🎯 SIGNAL
   Understands the dynamic data requirement. Why tools exist conceptually.
```

---

**Q23: "How does the system handle a slow or unavailable PNS API?"**
```
💡 APPROACH
   Talk about the retry and timeout config. Then the error return strategy.
   Show you thought about degraded state behaviour.

✅ SAY
   "Each tool has a 45-second timeout and 3 retry attempts with exponential
   backoff. If DCC is slow but recovers within 90 seconds, the retry handles it
   transparently. If it's still unavailable after all retries, the tool returns
   a structured error message — not a thrown exception.

   This matters because if a tool throws, LangChain4j propagates the error
   up and the agent has no information. If a tool returns 'DCC unavailable:
   timeout after 45s', the LLM can acknowledge this in its response —
   'carrier configuration could not be retrieved because DCC is not responding'
   — which is much more useful to an on-call engineer than a generic error.

   The conversation is still saved to Cassandra. The degraded response is
   better than no response when you're debugging an incident."

⛔ DON'T SAY
   "It throws an error." → Doesn't show design thinking.

🎯 SIGNAL
   Resilience thinking. Graceful degradation design.
```

---

**Q24: "What are the security considerations in this system?"**
```
💡 APPROACH
   JWT auth, PingFederate SSO, market access control, data residency.
   Also mention what's missing as a senior signal.

✅ SAY
   "Several layers. Authentication uses PingFederate SSO — Walmart's
   enterprise identity provider. Every API request carries a JWT token
   validated on the backend. Market access is separately enforced —
   a user must have explicit permission for the Mexico or Canada market,
   not just a valid login.

   On the data side: all LLM calls go through Azure OpenAI — data stays
   in Walmart's Azure tenant. Milvus is self-hosted for the same reason.

   One area that's not production-complete is the auth implementation —
   the current JWT handling is custom code rather than Spring Security's
   standard OAuth2 resource server. It works but it's hand-rolled.
   Production hardening would migrate to standard OAuth2 OIDC."

⛔ DON'T SAY
   "It's secure because we use HTTPS." → Too generic.
   Pretending the auth is fully production-hardened.

🎯 SIGNAL
   Honest about limitations. Knows what production security looks like.
```

---

## CATEGORY 6 — What Would You Change? (2 Questions)

---

**Q25: "If you had another month on this project, what would you focus on?"**
```
💡 APPROACH
   Don't say "more features." Say: better observability, testing, or
   hardening. Show engineering maturity, not feature hunger.

✅ SAY
   "Three things I'd prioritise.

   First, a tool testing framework — unit tests for every tool with mocked
   API responses. Right now we only validate end-to-end, which means slow
   iteration when something goes wrong.

   Second, distributed tracing. We log token counts and tool calls, but we
   don't have a trace ID that links the HTTP request through the Azure OpenAI
   call through each DCC or Wakanda API call. Debugging latency spikes is
   hard without that end-to-end trace.

   Third, rate limiting. High-volume users could exhaust Azure OpenAI quota.
   Per-user rate limiting with graceful backoff messages would protect the
   shared quota."

⛔ DON'T SAY
   "I'd add more features." → Not engineering maturity.

🎯 SIGNAL
   Production mindset. Understands observability, testing, and resilience
   as first-class engineering concerns.
```

---

## CATEGORY 7 — Behavioural (3 Questions)

---

**Q26: "Tell me about a time you had to learn something completely new quickly."**
```
💡 APPROACH
   Use the AI agent work directly. This is literally that story —
   you had PNS expertise, had to quickly get up to speed on
   LangChain4j, LLMs, RAG, and vector databases.

✅ SAY
   "This project was that experience. I had deep PNS domain expertise —
   5 years on MCSE — but had not worked with LLM-based agents before.
   LangChain4j, vector databases, embeddings, RAG — none of that was
   in my background.

   My approach was to start with the mental model before the syntax.
   I first understood conceptually what an agent does — LLM plus tools plus
   memory — then mapped our PNS systems to that model: DCC and Wakanda are
   tools, Cassandra is memory, the system prompt is the briefing. Once that
   mapping was clear, learning the specific LangChain4j APIs was
   straightforward — I knew what I was trying to build.

   The thing I underestimated was how iterative prompt engineering is.
   I expected to write the system prompt once and be done. In practice,
   every gap in the prompt showed up as a wrong answer from the agent.
   It took many cycles of: observe wrong answer → trace the reason →
   add or refine the rule → retest."

⛔ DON'T SAY
   "I read the documentation." → Not a strategy.

🎯 SIGNAL
   Self-directed learning. Mental model first approach. Honest about
   the learning curve.
```

---

**Q27: "How did you work with the team on this?"**
```
💡 APPROACH
   Show two-way dependency. You needed the infrastructure.
   The infrastructure needed your domain knowledge.

✅ SAY
   "Clear ownership boundaries made the collaboration work. The team owned
   the AI infrastructure — LangChain4j config, Cassandra schema, Milvus setup,
   Spring Boot wiring. I owned the domain layer — tools, system prompt,
   PNS handler. Neither could validate their work without the other.

   In practice: I gave the team the tool interface contracts before the
   infrastructure was ready — what tools I'd need, what signatures, what
   return types. They built against those contracts. When the infrastructure
   was available, I integrated my implementations and ran end-to-end tests.

   The iteration happened together — when an agent answer was wrong, we'd
   debug collaboratively: was the tool returning wrong data (infrastructure
   side) or was the system prompt giving wrong guidance (my side)? That
   kind of cross-boundary debugging required both of us."

⛔ DON'T SAY
   "They built it and I tested it." → Passive role.

🎯 SIGNAL
   Clear ownership. Collaborative problem-solving. Interface-first thinking.
```

---

**Q28: "What is the most important thing you learned from this project?"**
```
💡 APPROACH
   Something genuine and specific. Not generic "teamwork is important."

✅ SAY
   "That domain knowledge is a first-class engineering input in AI systems —
   not just a requirement document handed to developers.

   In traditional backend development, a domain expert defines requirements
   and engineers implement them. In an AI agent, the domain knowledge IS the
   implementation — it lives in the tool descriptions, in the system prompt,
   in the validation of whether the AI's answers are correct. You can't
   separate 'what the system should do' from 'how the AI reasons about it.'

   That changed how I think about AI projects. The engineers who will build
   the most useful AI systems are the ones who combine technical skills with
   deep domain expertise — not the ones who know the most about transformers
   or vector databases. The hard part is encoding domain reasoning correctly."

⛔ DON'T SAY
   "I learned LangChain4j." → Too shallow.

🎯 SIGNAL
   Philosophical depth. Shows you extracted genuine insight from the work,
   not just technical skills.
```

---

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✅  INTERVIEW READY — You now have scripted approaches for 28
    questions across 7 categories.
    Stop here if short on time. Practise these out loud.
    Deep Dive below covers edge cases and advanced probing.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## 🔬 DEEP DIVE — 5 Advanced Technical Questions

---

**Q29: "How does the context window limitation affect your system design?"**

> "Concretely: every prompt includes the system prompt (~800 tokens), the top 5
> RAG documents (~1,500 tokens), the tool definitions for 40+ tools (~2,000 tokens),
> and up to 30 messages of conversation history. That's ~4,300 tokens before the
> user's actual message. With an 8,192 token limit, we have roughly 3,800 tokens
> left for history and the answer. The 30-message cap on memory is a direct
> consequence of this constraint. If we needed deeper history, we'd either need
> a larger context model or a summarisation step to compress older messages."

---

**Q30: "What is the difference between fine-tuning and RAG? Which would you use?"**

> "Fine-tuning updates the model's weights — it bakes knowledge into the model
> permanently. RAG retrieves knowledge at runtime — the model doesn't change,
> it just gets relevant context injected. For our use case, RAG is clearly
> better: our PNS documentation updates frequently, and fine-tuning would
> require retraining the model every time a runbook changes. RAG lets us
> update Milvus with new documents and the agent immediately has access
> to them. Fine-tuning would make more sense for teaching the model a
> specific reasoning style or domain-specific language patterns that don't
> change often."

---

**Q31: "How would you scale this if user load increased 10x?"**

> "Three bottlenecks at 10x load. First, Azure OpenAI — we'd hit the rate
> limit. Solution: configure quota increases with Azure and implement a
> token budget per user per day. Second, Cassandra — write throughput scales
> horizontally, add nodes. Third, Milvus — the similarity search is CPU-bound.
> Milvus supports GPU-accelerated indexing and sharding across nodes. The
> Spring Boot layer is stateless — scale horizontally behind a load balancer.
> The main architectural constraint is the LLM API quota, not our own systems."

---

**Q32: "How do you prevent the AI from hallucinating PNS-specific data?"**

> "Three mechanisms. First, tools — the AI doesn't generate carrier method
> SLAs or template IDs, it fetches them from DCC. Hallucination requires
> the LLM to make up data it doesn't have; tools replace made-up data with
> real API responses. Second, temperature 0.2 — near-deterministic output
> reduces creative embellishment. Third, the system prompt explicitly instructs
> the agent to say 'data not available' if a tool returns empty or fails,
> rather than filling gaps with guesses. The remaining hallucination risk is
> in the synthesis step — how the LLM connects tool results. Validation
> against known cases is the mitigation there."

---

**Q33: "Why store conversation history in Cassandra instead of the LLM's own memory?"**

> "The LLM has no persistent memory between sessions — each API call is
> stateless. If you want the agent to remember the previous message, you
> have to include it in the prompt. LangChain4j's MessageWindowChatMemory
> manages this, but it needs a backing store for persistence across server
> restarts and for multi-instance deployments. An in-memory store would
> lose history on restart. Redis would work but Cassandra gives us
> durability, horizontal scalability, and time-series query patterns
> naturally. The team already ran Cassandra for MCSE — operational
> familiarity was the tiebreaker."

---

## 🔬 DEEP DIVE — How to Handle "I Don't Know"

Two phrases. Either is honest and senior.

**When you partially know:**
> "The way I'd reason about it is... [say what you do know]
> ...though I'd want to verify the exact implementation detail before being certain."

**When you genuinely don't know:**
> "I haven't gone deep on that specific piece — it was more in the
> infrastructure layer owned by the team. What I can speak to is how
> it connected to the domain layer I worked on, which was..."

Both phrases own your knowledge boundary clearly. Neither sounds like hiding.
The redirect to your strength is key — always have something to say after.

---

**Next: `05_RESOURCES.md` — YouTube links, cheat sheet, and day-before checklist.**
