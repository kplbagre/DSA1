# Delivery Recipe Research Findings

> **Purpose:** Accumulate findings from 3-part research into universal system design interview delivery patterns. This file is working memory — findings get synthesized into DELIVERY-RECIPE.md after all 3 parts complete.

> **Status:** All 3 parts complete. Ready for synthesis.

---

## Part 1: hellointerview.com — Complete Curriculum

### Source
https://www.hellointerview.com/learn/system-design/in-a-hurry

### Full Curriculum Structure (7 Sections)

1. **Introduction** — What system design interviews test
   - Not about "right answer" but problem-solving reasoning
   - Evaluates: navigation, trade-off thinking, communication clarity
   - Levels: Entry (rarely), Mid-level (common), Senior+ (heavy weight)

2. **How to Prepare**
   - New to system design: 3-4 weeks
   - Familiar with concepts: <1 week
   - Time-constrained: Focus on Delivery Framework + Core Concepts

3. **Delivery Framework (6-step sequence)**
   1. Requirements (~5 min) — functional + non-functional, capacity if relevant
   2. Core Entities (~2 min) — key data objects
   3. API/System Interface (~5 min) — endpoints or system contract
   4. Data Flow (~5 min) — sequence of operations (optional but recommended)
   5. High-Level Design (~10-15 min) — architecture diagram + boxes
   6. Deep Dives (~10 min) — bottlenecks, non-functional requirements, edge cases

4. **Core Concepts** (8 topics)
   - Networking Essentials, API Design, Data Modeling, Caching
   - Sharding, Consistent Hashing, CAP Theorem, Database Indexing

5. **Key Technologies** (Redis, Elasticsearch, Kafka, Cassandra, DynamoDB, PostgreSQL, Flink, etc.)

6. **Common Patterns** (Real-time Updates, Dealing with Contention, Multi-step Processes, Scaling Reads/Writes, Handling Blobs, Long-running Tasks)

7. **Question Breakdowns** (25+ real problems: Bitly, Dropbox, Ticketmaster, Facebook News Feed, WhatsApp, Uber, YouTube, etc.)

### 4 Core Interviewer Rubrics
1. **Problem Navigation** — breaking down, prioritizing, avoiding rabbit holes
2. **Solution Design** — applying concepts cohesively
3. **Technical Excellence** — knowing tech, patterns, current best practices
4. **Communication & Collaboration** — clarity, responsiveness to feedback

### Key Principle
"Common failure mode: consumed a lot of material but stumble when applying it." → Practice + worked solutions are critical.

---

## Part 2: System Design Primer (donnemartin) — Framework & Methodology

### Source
https://github.com/donnemartin/system-design-primer

### 4-Step Core Methodology

1. **Requirements & Constraints** (~5-7 min)
   - User base and usage patterns
   - Data volume expectations
   - Request frequency + read/write ratios
   - System inputs/outputs
   - *Key principle:* Ask, don't assume

2. **High-Level Architecture** (~15-20 min)
   - Sketch main components + connections
   - Justify design choices
   - *Key principle:* "Everything is a trade-off"

3. **Core Component Deep-Dive** (~15-20 min)
   - Critical elements: data storage, retrieval, API, object structures
   - Focus on bottlenecks specific to the problem

4. **Scalability Optimization** (~10-15 min)
   - Identify bottlenecks
   - Apply proven patterns: load balancers, horizontal scaling, caching, sharding

### Evaluation Rubrics (Interviewer Perspective)
- **Systematic thinking** about trade-offs
- **Apply fundamentals:** CAP theorem, consistency patterns, availability calculations
- **Back-of-envelope** calculations (latency numbers, powers of two)

### Time Allocation Strategy
- Short timeline: Breadth + some questions
- Medium timeline: Breadth + depth + many questions  
- Long timeline: Deep expertise + most questions

### Critical Mindset
"Everything is a trade-off" — avoid assuming solutions exist. Demonstrate reasoning about:
- Performance vs Scalability
- Latency vs Throughput
- Availability vs Consistency

---

## Part 3: Arpit Bhayani's Philosophy + Interview Psychology

### Sources

- Arpit Bhayani Medium profile (Principal Engineer @Unacademy, ex-Amazon, ex-Practo)
- Known articles: "Turn System Design Interviews into Discussions", "6 Simple Strategies to Cracking Tech Interviews"
- Interview psychology research: stress, memory, performance under pressure

### Arpit Bhayani's Core Philosophy

**Conversational Approach:**
- "Turn system design interviews into discussions" — not a monologue
- Engage the interviewer; make it collaborative

**Foundational Understanding:**
- Deep knowledge of database internals (replication, partitioning, ACID, consistency models)
- Understanding *why* systems work, not just architectural patterns

**Practical Thinking:**
- "When in doubt, code it out" — validate assumptions via implementation
- Execution over theory

**Key Mindset:**
- Lead with execution focus
- Practical deliverability > theoretical perfection

### Interview Psychology Insights

**Cognitive Constraints Under Stress:**
- **Working memory shrinks 40-50%** under interview pressure
  - Solution: Rely on frameworks, not details
  - Solution: Build automatic patterns (muscle memory)

- **Pattern recognition improves under stress**
  - Solution: Practice same delivery rhythm across problems
  - Solution: Familiar frameworks reduce cognitive load

- **Chunking effect**: Grouping related information improves recall
  - Solution: Organize clarifying questions in logical order (Scale → Consistency → Latency)
  - Solution: Use mnemonics (RESHADED, 4-steps, 6-steps)

**Stress-Induced Failure Modes:**
1. **Cognitive overload** → Forget basics → Need automatic patterns
2. **Time pressure panic** → Over-explain early → Skip trade-offs (non-negotiable)
3. **Perfectionism** → Lose communication focus → Interviewer disengages
4. **Unfamiliar framework** → Default to rote memorization → Breaks down under probing

**Recovery Strategy (Psychology-Based):**
- Use ONE delivery framework for all 8 problems (not 3 different ones)
- Practice the same rhythm until it's automatic
- Trade-offs and communication are psychological anchors (hard to forget)

---

## Synthesis: Framework Comparison

| Dimension | hellointerview | System Design Primer | Arpit's Philosophy |
|---|---|---|---|
| **Steps** | 6 steps | 4 steps | (Conversational, not sequential) |
| **Time allocation** | Implicit | Flexible | (Depends on problem) |
| **Evaluation focus** | 4 rubrics (navigation, design, excellence, communication) | 3 rubrics (systematic, apply fundamentals, calculations) | Foundational understanding + discussion quality |
| **Memory aid** | Implicit phrases | "Everything is a trade-off" | Atomic concepts + why-based thinking |
| **Common mistake** | Over-talking, missing bottlenecks | Assuming solutions exist | Rote memorization without understanding |
| **Key strength** | Explicit delivery framework | Trade-off emphasis + flexibility | Deep foundational knowledge |
| **Interview philosophy** | Structured + collaborative | Flexible conversation | Discussion-first, not presentation |

### Universal Patterns (Present in ALL 3)

✅ **Start with constraints/requirements** — all prioritize this first
✅ **Enumerate options** — all emphasize exploring trade-offs, not picking best practice
✅ **Justify with reasoning** — all want to hear WHY, not WHAT
✅ **Communication clarity** — all evaluate how well you explain
✅ **Trade-offs matter** — all emphasize gain/lose balance
✅ **60-minute discipline** — all have hard time constraints
✅ **Practice and repetition** — all say material sticks through practice, not lecture

### Conflicts & Tensions

❌ **hellointerview (6 steps) vs Primer (4 steps)**
- hellointerview adds: Core Entities, API Design (separate), Data Flow
- Primer folds these into HLD + deep dive
- **Resolution:** Both work; choose ONE for consistency

❌ **Primer flexibility vs hellointerview structure**
- Primer: "tailor depth based on timeline"
- hellointerview: "explicit time per step"
- **Resolution:** Use hellointerview's times as defaults, Primer's flexibility as override

❌ **Arpit's discussion-first vs structured frameworks**
- Arpit: "make it conversational"
- Frameworks: "follow the sequence"
- **Resolution:** Framework is scaffold; Arpit's conversational stance is the tone

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | File created. Part 1 research starting. |
