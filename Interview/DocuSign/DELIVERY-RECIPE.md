# System Design Interview Delivery Recipe

> **What this is:** A universal 6-step interview delivery framework backed by cognitive psychology and tested across 3 major interview preparation methodologies. This recipe works for ANY company (Google, Amazon, Meta, Stripe, DocuSign) — the only company-specific part is the final evaluation checklist.
>
> **How to use:** Read once to understand the mental model. Before each interview, skim the "Memory Anchors" section. During the interview, follow the 6-step sequence. The framework becomes automatic with practice.
>
> **Time to read:** 30 minutes (includes mental models). Worth every second before your first interview.

---

## 🧠 Mental Model — The 6-Step Dance

Think of a system design interview like **building a house with the owner present:**

1. **Requirements** — Before drawing blueprints, you ask the owner: "How many people will live here? How many stories? Any pets?" (you clarify constraints, not assume)
2. **Core Entities** — You name the main rooms: "living room, kitchen, bedrooms, bathrooms" (identify the key pieces)
3. **API/Interface** — You define how people move through the house: "front door here, hallway there, stairs lead up" (design the contract)
4. **Data Flow** — You trace a path: "person enters front door → walks through hallway → reaches kitchen → gets water from faucet" (show the journey)
5. **High-Level Design** — You sketch the blueprint: "ground floor has kitchen, bedroom 1; second floor has bedroom 2, master bedroom; basement has utilities" (draw the architecture)
6. **Deep Dives** — You zoom in on critical details: "foundation must handle earthquakes, HVAC must cool 4,000 sq ft, roof must withstand snow load" (address bottlenecks)

**The key:** You don't jump to "let me draw the blueprint." You ask, clarify, identify pieces, show movement, sketch the whole thing, then dive deep where it's risky.

---

## ⏱️ The 6-Step Framework (60-Minute Budget)

### Step 1: Requirements & Constraints (~5 minutes)

**What you do:**
Ask clarifying questions. Don't assume. The interviewer is watching how you think, not how fast you talk.

**Mental model:**
You're a detective. The interviewer gives you a vague crime scene ("design a messaging system"). Your job is to gather facts before forming a hypothesis.

**Questions to always ask (in this order):**
1. **Scale:** "How many daily active users? Messages per day?" (justifies all capacity decisions downstream)
2. **Consistency:** "Do messages need strict ordering, or eventual consistency OK?" (determines if you need a sequence number)
3. **Latency:** "What's the user experience expectation? Under 100ms? Under 5 seconds?" (shapes whether you cache)
4. **Scope:** "1:1 messaging only, or groups too? If groups, what size?" (determines fan-out strategy)
5. **Feature scope:** "Media attachments in scope, or text only?" (determines storage layer)

**Why this order matters (psychology):**
- Scale first: biggest constraint, impacts everything
- Consistency next: shapes schema + API contract
- Latency next: shapes caching + protocol choice
- Scope next: determines complexity
- Features next: separates MVP from full product

**Common mistake:**
Asking "what technology should I use?" or "should I use Kafka?" before understanding constraints. You'll sound like you're pattern-matching, not reasoning.

**Red flag to avoid:**
Spending >5 minutes here. After 5 min, say: "Let me assume [your assumptions]. Should I adjust anything?"

---

### Step 2: Core Entities (~2 minutes)

**What you do:**
Name the key data objects. Not the schema, not the API — just the nouns in the system.

**Example (for a messaging system):**
- **Message** (the core data)
- **Conversation** (collection of messages)
- **User** (sender/recipient)
- **Presence** (online status)

**Mental model:**
You're an archaeologist. Before you excavate the whole site, you walk around and spot the major artifacts. You don't dig yet — you just mark them.

**Why this step matters (psychology):**
Under stress, your brain forgets details but remembers *categories*. Naming entities creates buckets your brain can organize around. Later, when you're deep in trade-offs, you'll say "the Message entity needs ordering" — your brain already has a place to file that.

**Common mistake:**
Conflating entities with tables/schemas. An entity is a *concept*. The **Message** entity might live in Cassandra, Redis, and PostgreSQL simultaneously — you're not designing the schema yet.

---

### Step 3: API/System Interface (~5 minutes)

**What you do:**
Define how external systems interact with your design. Not the internal communication (that's Step 4) — the *boundary contract*.

**Example (for messaging):**
- POST `/messages/{conversationId}` — send a message
- GET `/messages/{conversationId}?cursor=X&limit=50` — fetch messages
- POST `/conversations` — create a conversation
- WebSocket wss://... — receive messages in real-time

**Mental model:**
You're defining a language. The API is the *syntax*. When the user (or internal service) wants to talk to your system, they speak this language.

**Why this step (psychology):**
It locks down the *contract* before you argue about implementation. If you define the API first, the interviewer can't blindside you with "but what if the user does this?" — you've already thought about it.

**Common mistake:**
Designing the API **after** the architecture. This is backwards. The API is a constraint, like the requirements. It shapes how you architect.

---

### Step 4: Data Flow (~5 minutes)

**What you do:**
Trace a single request from entry to exit. "User sends message" → "message persisted" → "recipient notified". Tell the story sequentially.

**Example walkthrough (say this out loud):**
> "User A sends a message to Conversation X. The message enters the WebSocket connection server. The server writes it to Kafka for durability. The Message Service consumes from Kafka, persists to Cassandra, looks up User B's current connection server in Redis. If User B is online, sends the message via WebSocket. If offline, routes to Push Notification Service. User B's device sends a 'delivered' ACK. Service updates the message status in Cassandra. User A's device receives the status update."

**Mental model:**
You're drawing a treasure map with X marking the destination. You don't fill in every beach — you just show the path from start to finish.

**Why this step (psychology):**
The interviewer is checking: "Does this person understand the *flow*, or just the boxes?" A scattered answer ("we have Kafka... also Cassandra... also Redis... also push notifications") makes you sound lost. A traced flow makes you sound in control.

**Common mistake:**
Making the data flow too detailed. You're not designing the schema or the API yet — you're just showing movement through the system.

---

### Step 5: High-Level Architecture (~15 minutes)

**What you do:**
Draw the boxes and arrows. ASCII diagram or whiteboard. Here's what the system looks like from 10,000 feet.

**Example structure:**
```
[Client] → [Load Balancer] → [API Server] → [Database]
                                    ↓
                              [Cache]
                                    ↓
                              [Message Queue]
                                    ↓
                            [Background Worker]
```

**Mental model:**
You're a city planner. The database is the library (where knowledge lives). The cache is the convenience store (quick access). The message queue is the postal system (reliable delivery). The load balancer is the highway (traffic management).

**What to include:**
- **Synchronous paths** (request-response)
- **Asynchronous paths** (message queues, workers)
- **Storage layers** (databases, caches)
- **Stateless vs stateful** components

**What NOT to include yet:**
- Specific technologies by name ("PostgreSQL", "Redis")
- Exact data models
- Replication/sharding strategies
- Monitoring, logging, tracing

**Why this step (psychology):**
The HLD is your "mental model made visible." Once it's on the whiteboard, the interviewer can see your thinking. They can also probe specific boxes without you having to redesign the whole thing.

**Time allocation within this step:**
- 2 min: draw the boxes
- 3 min: walk through the data flow (trace it again)
- 5 min: justify each box ("Why Kafka? Why cache here, not there?")
- 5 min: handle the interviewer's first questions

**Common mistake:**
Drawing too many boxes too early. Start simple. Add complexity only when asked.

---

### Step 6: Deep Dives (~10 minutes, but flexible)

**What you do:**
Pick 2-3 *riskiest* components and design them in detail.

**Riskiest** means:
- Where the system is most likely to fail (bottleneck)
- Where scale hits hardest (e.g., database at 1M writes/sec)
- Where the problem is unique (not standard solution)

**Example deep dives (for a messaging system):**
1. **Message storage schema** — "Why Cassandra? How is the partition key chosen? What about ordering?"
2. **Fan-out strategy** — "For groups of 100K, do we fan-out on write or read? What's the trade-off?"
3. **Offline delivery** — "How do we ensure messages aren't lost when the user is offline?"

**Mental model:**
You're a safety inspector. You don't inspect every room in the house — you inspect the foundation, the electrical panel, and the roof (the places where failure is catastrophic). You ignore the paint color.

**What to prepare:**
- One SQL/NoSQL schema with comments explaining choices
- One algorithm (fan-out, sharding, caching strategy) with pseudocode
- One trade-off comparison ("option A: fast writes, slow reads" vs "option B: slow writes, fast reads")

**Why 2-3, not more:**
Your working memory is shrinking 40-50% under stress. If you try to deep-dive on 5 things, you'll confuse them or run out of time. Pick the hardest 2-3.

**Common mistake:**
Picking the most *interesting* dives instead of the most *risky* dives. Avoid rabbit holes.

---

## ⚠️ Trade-offs (~8 minutes)

By the time you reach here, you've designed a complete system. Now you name the trade-offs you made.

**Format for each trade-off:**
> "For [component], I chose [option A] over [option B]. I gain [benefit], but I lose [cost]. If [condition changes], I'd reconsider."

**Example:**
> "For group chat fan-out, I chose write fan-out for groups ≤100. I gain fast reads (pre-populated inbox), but I lose write scalability for large groups. If the interviewer said 'groups go up to 100K', I'd switch to read fan-out."

**Why this matters (psychology + rubric):**
The interviewer's rubric says: "Avoid fixation on optimal solutions. Show trade-off thinking." This is where you prove you understand constraints vs optimality.

**Time allocation:**
- 2 min per trade-off × 3 trade-offs = 6 minutes
- 2 min buffer for interviewer follow-up

**Non-negotiable:**
Always include trade-offs. If you run out of time, cut a deep dive, not trade-offs. The rubric values trade-off thinking over technical depth.

---

## 🧠 The Psychology Behind This Recipe

### Why ONE Framework (Not 3)?

You researched 3 frameworks:
1. hellointerview (6 steps)
2. System Design Primer (4 steps)
3. Arpit's philosophy (conversational)

**Why we chose the 6-step as the base:**

Under interview stress, your working memory shrinks 40-50%. You can't juggle 3 different frameworks. You need ONE rhythm that becomes **automatic**. The 6-step is:
- Explicit (each step has a clear purpose)
- Mnemonic-friendly (easier to remember than 4 steps or 0 steps)
- Complete (covers all phases of a real interview)

After practicing all 8 problems with the same 6-step rhythm, your brain will **execute it without thinking** — like playing a familiar song on piano.

### Cognitive Load: What Your Brain Is Doing

**Minute 0-10 (Requirements + Entities + API):**
Your brain is **fully engaged** in clarifying. You're asking questions, listening, taking notes. Cognitive load is HIGH but manageable because the task is simple (ask, don't design).

**Minute 10-25 (Data Flow + HLD):**
Your brain is **translating constraints into architecture**. Cognitive load is HIGHEST. This is where candidates often panic and skip to "let me just draw the boxes."

**Workaround:** Use the data flow step as a bridge. It's a narrative (easier on working memory) before the spatial diagram (harder on working memory).

**Minute 25-50 (Deep Dives):**
Your brain is **zooming in**. Cognitive load drops because you're no longer holding the whole system in mind — just one component.

**Minute 50-60 (Trade-offs + Q&A):**
Your brain is **reflecting**. Cognitive load is low. You're comparing options, not inventing them.

### Stress-Induced Failure Modes (And How This Recipe Prevents Them)

| Failure Mode | What Goes Wrong | How This Recipe Prevents It |
|---|---|---|
| **Cognitive overload** | Candidate tries to design everything at once | Step 1 forces you to clarify first; you don't design without scope |
| **Time pressure panic** | Candidate over-explains early steps and skips trade-offs | Explicit time budget keeps you on track; trade-offs are protected |
| **Perfectionism** | Candidate re-draws the HLD 3 times | HLD is step 5, not step 1; by then you have constraints to guide you |
| **Unfamiliar framework** | Candidate defaults to rote memorization ("I'll use Kafka") | This recipe is a *thinking* framework, not a tech checklist |
| **Memory loss** | Candidate forgets requirements mid-interview | Entities + API + data flow create memory anchors your brain can cling to |

---

## 💾 Memory Anchors — What to Memorize

You don't need to memorize every detail. Memorize **these 6 sentences:**

1. **"Ask before you design."** → Requirements first.
2. **"Name the nouns."** → Entities.
3. **"Define the boundary."** → API.
4. **"Trace a request."** → Data flow.
5. **"Draw the boxes."** → HLD.
6. **"Dig where it's risky."** → Deep dives.

**Bonus anchors (if you have space in your memory):**
- **"Everything is a trade-off."** (System Design Primer's mantra)
- **"Why, not what."** (Arpit's philosophy)
- **"Conversational, not presentation."** (Interview tone)

---

## 🔐 DocuSign-Specific: Evaluation Dimensions

**This section is DocuSign-only. Skip if prepping for another company.**

After your 60-minute answer, DocuSign interviewers mentally check these 7 dimensions. During your answer, **you** should explicitly mention them.

### The 7 Dimensions (From DocuSign PDF)

After finishing your HLD, pause and say:

> "Let me map this design to the evaluation dimensions:
> - **Scalability:** [how your design handles 10×, 100× load]
> - **Availability:** [uptime SLO, fault tolerance strategy]
> - **Security:** [encryption, authentication, access control]
> - **Observability & Traceability:** [monitoring, logging, trace IDs]
> - **Extensibility:** [how you'd add new features without rewriting]
> - **Testability:** [which components can be tested in isolation]
> - **Usability:** [API clarity, error messages, developer experience]"

**Example (for a chat system):**
> "Scalability: Cassandra shards by conversation_id; WebSocket servers via consistent hashing. Availability: Kafka decouples writes from notification; push fallback for offline users. Security: TLS in transit, AES-256 at rest, JWT auth. Observability: trace_id injected at entry, propagated through Kafka headers. Extensibility: notification delivery is a Strategy interface — adding SMS means adding a new implementation. Testability: Message Service accepts mocked dependencies. Usability: API uses standard HTTP verbs; error responses are structured."

**Why this matters:**
DocuSign's PDF says their rubric is these 7 dimensions. Mentioning them explicitly signals: "I read your PDF, I understand your values." You're not trying to dazzle them with depth — you're showing you understand how THEY evaluate.

---

## 🎬 Full Example: Putting It Together

**Question:** "Design a URL shortener."

**Your answer (60 seconds, skeletal):**

> **Requirements (2 min):** "How many URLs shortened per day? Read-to-write ratio? Availability target? Should shortened URLs work across regions or single-region?"
>
> **Entities (1 min):** "User, OriginalURL, ShortenedURL, Click (for analytics)."
>
> **API (3 min):** "POST /shorten { long_url } → { short_url }. GET /{short_url} → redirect(long_url). GET /analytics/{short_url} → { clicks, top_regions }."
>
> **Data Flow (3 min):** "User pastes long URL → calls POST /shorten → service generates a unique short code → stores mapping in DB → returns short URL. Later, user clicks short link → GET /{short_code} → DB lookup → redirect."
>
> **HLD (12 min):** [Draw diagram: Load balancer → API servers → DB (with replication) + Cache + Click counter service.]
>
> **Deep Dives (8 min):**
> - Collision handling (how do you ensure short codes are unique?)
> - Click counting at scale (how do you handle millions of clicks without overloading the DB?)
>
> **Trade-offs (5 min):**
> - Collision strategy: deterministic vs random. Gain: random is stateless; lose: need collision detection.
> - Click counting: write-heavy counter vs periodic aggregation. Gain: real-time accuracy; lose: DB bottleneck.
>
> **DocuSign dimensions (3 min):** [Map to 7 dimensions as shown above.]

That's roughly 60 minutes. You've covered the whole system, shown your thinking, addressed trade-offs, and signaled you understand DocuSign's values.

---

## 🧪 Before Your First Interview

**Checklist:**
- [ ] Read this recipe once (30 min)
- [ ] Practice the 6-step rhythm on 2 example problems (30 min per problem)
- [ ] Draw HLDs by hand on paper (not typed) — hand-drawing is how interviews work
- [ ] Time yourself: Can you fit all 6 steps in 60 minutes?
- [ ] Memorize the 6 memory anchors
- [ ] Read the DocuSign dimensions section

---

## 📚 References (For Future Reading)

These sources informed this recipe. They're optional deepeners, not prerequisites.

**Essential (if you want to understand the philosophy):**
- hellointerview.com — "Learn System Design in a Hurry" — https://www.hellointerview.com/learn/system-design/in-a-hurry
  - Why read: Explicit delivery framework, 4 core rubrics, emphasis on communication
- System Design Primer (donnemartin) — https://github.com/donnemartin/system-design-primer
  - Why read: Trade-off thinking emphasis, flexible time allocation strategy
- Arpit Bhayani's articles on Medium — https://medium.com/@arpitbhayani
  - Why read: Deep conceptual foundation, "why" over "what", foundational knowledge emphasis

**Interview psychology (optional):**
- "How to Work Under Pressure" — cognitive load research shows working memory shrinks 40-50% under stress
- Concept of chunking in memory — why one framework is better than three

**DocuSign-specific:**
- DocuSign Technical Interview Guide (PDF) — covers the 7 evaluation dimensions, 2 interview types, 60-minute format

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | Recipe created. Based on synthesis of 3 frameworks (hellointerview, System Design Primer, Arpit's philosophy) + interview psychology research. Structure: 6-step universal framework + psychology rationale + DocuSign appendix. Designed to be self-contained, memorable, and portable to any company. |
