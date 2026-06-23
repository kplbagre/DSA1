# Spring Prep — Log & Context-Preservation File

> **READ THIS FIRST if you're picking up cold (new chat window, new AI, fresh session).** This file is the single source of truth for where the Spring foundation track stands. Sections 1, 4, 6 are the **quick-resume**; Sections 2, 3, 5, 7, 8 are the **rich context**.

---

## 1. 🎯 Quick-resume for a fresh AI/chat

**Who:** Kapil — senior Java engineer, prepping for a **senior backend / SDE-3 interview ~30 days out (as of May 2026)**. Has shipped Spring code in production (eg, in your app) for years but feels **conceptually weak** — needs the WHY underneath the annotations, not the HOW of the syntax.

**What this folder is:** A focused **10-hour, 10-day Spring foundation track**. Runs in parallel with the DSA prep in `../../DSA/`. The two tracks are designed not to collide on heavy days.

**Locked decisions (do not relitigate without explicit user request):**
- **Path A** chosen — Tier-1 core topics only (Servlets, IoC/DI, AOP, Spring MVC, Spring Boot auto-config, JPA, properties/profiles)
- **Tier-2 deferred** (post-interview): Spring Security, Spring Cloud, Kafka+Spring, advanced testing
- **Cadence:** 1 hr/day × 10 days, written **in parts** — each day's DeepDive + practice generated just before that day, informed by feedback from the previous day
- **Note style:** universal `../../AGENTS.md` rules + JavaBackend `../AGENTS.md` rules apply
- **Three custom inclusions** (added based on interview-focused Q&A round):
  1. **Mock Interview Q&A appendix** at the end of every DeepDive note
  2. **"Where you've seen this in your app"** callouts with real `mcse_lite` snippets from the reference codebase
  3. **Running bug/lesson log** (Section 7 of this file)

**Where everything lives:**
```
JavaBackend/Spring/
├── spring-prep-log.md         ← YOU ARE HERE
├── spring-10-hour-plan.md     ← Day-by-day master plan
├── DeepDive/                  ← Per-day concept notes (written one at a time)
├── Reference/                 ← Cheatsheets (built as topics complete)
└── Practice/                  ← Runnable Java code
    ├── README.md              ← Setup + how-to-run
    ├── exercises/             ← Per-topic drills
    └── growing-app/           ← Same endpoint, 3 iterations (servlet → Spring MVC → Spring Boot)
```

**How to resume work:**
1. Read this file's Section 4 (status table) → find first row with `TODO` or `IN PROGRESS`
2. Read Section 6 (what's next) → confirm the immediate next action
3. Read the previous DeepDive note (if any) to understand the prerequisite mental model
4. Generate the next DeepDive + exercise, following the per-day format in `spring-10-hour-plan.md`
5. **Update this file** at the end of the session — table status, session log entry, bug log if applicable

---

## 2. 📋 Original brief & locked decisions

### How we got to Path A

Kapil's original ask: build proper Spring mental model in 2-3 weeks (~36 hours). Pushed back honestly that his answers (explore-all + growing-app + interview-favorite tilt + conceptually-weak across the board) demand ~25-30 hours, but he had only 10 hours available. Offered three resolution paths:

| Path | What it kept | What it cut |
| --- | --- | --- |
| **A (chosen)** | 7 Tier-1 topics + growing app + per-topic exercises | Spring Security, Cloud, Kafka, advanced testing |
| B | All topics, breadth-only | Hands-on coding |
| C | Everything | Required extending to 20+ hours |

**Path A reasoning:** Interview-favorite topics ARE the Tier-1 set. Cloud/Kafka/Security usually surface as *"have you used X?"* questions answerable from work experience — not internals grilling. Stronger to have a deep model of 7 things than skim of 12.

### Pre-interview MCQ answers (locked in)

| Q | Answer | Implication |
| --- | --- | --- |
| Spring exposure at work | wants to explore ALL listed annotations | Broad familiarity goal — but scoped to Tier-1 for now |
| Goal | (d) Mix of interview-ready + work confidence | Notes tilt toward verbalization + work code |
| Time format | (b) 1 hr/day × 10 days | Daily cadence, manageable chunks |
| Shakiest topics | "worked on some, concept-wise weak" across the board | Need WHY not HOW; every annotation gets "what's behind it" |
| Servlets in/out | (a) Keep servlets in | Day 1-2 cover web fundamentals + servlet API |
| Hands-on appetite | (c) Build growing app across iterations | Same endpoint solved 3 ways (servlet → Spring MVC → Spring Boot) |
| Interview timing | (a) Yes — within 30 days | Tilt toward interview-favorite topics + Q&A appendix per note |

### Note format commitments (every DeepDive must follow)

- **English steps before code** (universal rule)
- **Wrong way in `// ❌ COMMON MISTAKE` comments + Why** at every pitfall (carry-over from DSA notes — high signal for interview traps)
- **ASCII diagrams** for spatial/sequential/stateful concepts (request lifecycle, container lifecycle, proxy interception, etc.)
- **🎤 Interview Q&A** appendix at end of every DeepDive (2-4 questions with model answers)
- **🏢 Where you've seen this in your app** callout with concrete `mcse_lite` snippet
- **📖 Prerequisite line** at top — what concepts the reader must already have before this note (link to earlier DeepDives)
- **🧾 TL;DR / mental hook** in 1-2 sentences at the END so the note is revisable in 30 seconds

---

## 3. 🧠 Kapil's profile & known traps

### Profile

- Senior Java engineer
- Years of Spring exposure (eg, in your app — `mcse_lite` and other internal projects) but **never went under the hood**
- Strong on writing code, weaker on **verbalizing concepts** in an interview setting
- Direct communicator — hates academic tone, wants "smart friend explaining"
- Likes the DSA notes' style: KEY STEPS first, then code, then 🐞 traps, then example problems

### Style preferences (carry-over from DSA work)

- One statement per line, always-brace single-statement blocks
- Spaces around operators (`int x = a + b`, not `int x=a+b`)
- Comments on their **own line** above the statement, not at end-of-line
- Picture + Invariant pattern for tricky concepts (ASCII diagram → 1-line algorithmic invariant)
- "Lesson learned the hard way (dated)" callouts for real failures
- Cross-references via explicit relative paths

### Known traps (will be updated as we go)

- *Conceptually weak on the WHY of Spring annotations* — has used them, hasn't questioned them. Every annotation introduced needs "what's happening under the hood" in a comment.
- *Will skip-read if a note feels academic* — keep it concrete, examples-first.

---

## 4. 📅 Day-by-day status table

| Day | Topic | Status | DeepDive | Reference | Practice | Time spent | Bugs hit |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1+2 | Web fundamentals + Servlet API — what's a servlet, container, request lifecycle, full Servlet API | **MERGED** | `DeepDive/01-web-servlet-foundation.md` ✅ | `Reference/01-web-fundamentals-reference.md` ✅ `Reference/02-servlet-api-reference.md` ✅ | reading + optional `nc -l 8080` hands-on + `exercises/01-servlet-hello/` ✅ + `growing-app/v1-servlet/` ✅ (both verified end-to-end on Jetty) | — | — |
| 3+4+5 | IoC/DI + Bean lifecycle + AOP & proxies — container, beans, ApplicationContext, DI types, scopes, bean lifecycle, `@Transactional` self-call trap | DRAFT | `DeepDive/02-spring-core.md` ✅ | `Reference/02-spring-core-reference.md` (TODO) | `exercises/02-bean-discovery/` (TODO) | — | — |
| 6+7+8 | Spring MVC + Spring Boot auto-config + Properties & profiles — DispatcherServlet routing, what `@SpringBootApplication` does, profiles, `@ConditionalOnMissingBean` (v2 + v3 of growing app) | TODO | `DeepDive/03-spring-mvc-boot.md` | `Reference/03-spring-mvc-boot-reference.md` | `exercises/05-dispatcher-trace/` + `growing-app/v2-spring-mvc/` + `growing-app/v3-spring-boot/` + `exercises/07-profiles-demo/` | — | — |
| 9+10 | Spring Data JPA + transaction propagation + lazy loading + consolidation + mock interview self-test | TODO | `DeepDive/04-jpa-transactions.md` | `Reference/04-jpa-transactions-reference.md` + `Reference/00-master-index.md` + `Reference/spring-annotations-cheatsheet.md` | `exercises/08-jpa-lazy-loading/` + walk all 3 growing-app versions, self-administered Q&A | — | — |

> **Status legend:** `TODO` (not started) → `IN PROGRESS` (notes/exercise being drafted) → `DRAFT` (DeepDive + Reference written, Kapil hasn't worked through it yet) → `DONE` (Kapil completed + bugs logged + retrospective added).
>
> **Per-day deliverable rule:** every day produces BOTH a DeepDive (`DeepDive/0X-topic.md`) and a Reference (`Reference/0X-topic-reference.md`). The Reference is written AFTER the DeepDive is complete, by distilling — it never adds new concepts. See `spring-10-hour-plan.md` → "🎤 Per-day note format" for the required Reference structure.

---

## 5. 🪜 Reference codebase (mcse_lite)

**Path:** `/Users/k0b077v/new_mcse/mcse_lite`

**What's there:**
- Multi-module Maven reference project — Spring (not Spring Boot, classical XML + Java config)
- 7+ files with `@RestController` / `@Controller` (e.g., `date-service-v3/.../HollowCacheServiceController.java`, `cache-service/.../PromiseImperiumServiceController.java`)
- Widespread `@Service` / `@Component` / `@Configuration`
- AOP example: `core/src/main/java/.../simulator/aspect/SimulatorOrchestratorAspect.java`
- Kafka consumer: `components/kafka-consumer/src/main/java/.../kafka/KafkaConsumer.java`
- `@Transactional` usage in `reservation/`, `date-service-v2/`
- `TECHNICAL_REFERENCE.md` and `INTERVIEW_PREP.md` at repo root — may have pre-documented patterns to leverage

**How to use this:** When writing each DeepDive note, grep mcse_lite for the concept being introduced and embed a real snippet in the "🏢 Where you've seen this in your app" callout. Snippets must be **copied verbatim with file:line attribution**, not paraphrased.

**Why it's NOT Spring Boot is actually useful:** mcse_lite is "what Spring looked like before Spring Boot." When we get to Day 7-8, Kapil will see the *before/after* of auto-config and appreciate what got abstracted away.

---

## 6. 🎯 What's next (immediate)

**Day 1 and Day 2 are DRAFT** — notes written, practice code built and end-to-end verified. Kapil hasn't read-through-and-recited the Day 2 Reference yet, so it's not DONE.

**Next session goal (after Kapil completes Day 2):** Day 3 — IoC + DI part 1 (container, beans, ApplicationContext).

**Files to create in next session:**
1. `DeepDive/03-ioc-di-container.md`
2. `Reference/03-ioc-di-container-reference.md`
3. `Practice/exercises/02-bean-discovery/` (minimal app that scans a package, prints all registered beans via `AnnotationConfigApplicationContext`)

**Pre-Day-3 prep checklist for the AI:**
- [ ] Read this prep-log Sections 1-3 in full
- [ ] Read `spring-10-hour-plan.md` Day 3 entry for scope
- [ ] Confirm Day 2 absorbed cleanly (ask Kapil: "did the lifecycle picture click? any traps hit?")
- [ ] If anything tripped, log it in Section 7 of this file
- [ ] Search `mcse_lite/components/` for `@Service` + `@Configuration` classes to reference in the Day 3 "in your app" callout
- [ ] Set up the Maven project structure for `02-bean-discovery/` — Spring Framework (NOT Boot — Day 3 is about the raw container, before Boot)

---

## 7. 🐞 Running bug/lesson log

> Every confusion, mistake, or "wait, that didn't work" moment gets a dated entry here. Format: short title + 1-3 line lesson + cross-link to relevant DeepDive.

### #1 — `nc -l 8080` looks "stuck" (2026-05-18) — Day 1

**What happened:** Hit Enter after `nc -l 8080`, expected confirmation or a prompt. Got just a new blank line. Felt like the command was incomplete.

**Lesson:** `nc -l` is a **blocking listener** — it silently binds to the port and waits. No "listening on port 8080" output. **This is correct behavior.** You verify it's working by opening a second terminal and running `curl http://localhost:8080/...` — the curl's raw HTTP request will then appear in the `nc` terminal.

**Generalization for Spring:** Many server-style commands (Tomcat startup, `mvn jetty:run`, `mvn spring-boot:run`) print log output, but raw socket tools (`nc`, low-level `ServerSocket` demos) don't. When in doubt: open another terminal and `lsof -i :PORT` to confirm something's actually bound.

**Cross-link:** [`DeepDive/01-web-servlet-foundation.md` — §🔬 Optional 5-minute hands-on](DeepDive/01-web-servlet-foundation.md)

---

## 8. 📚 Concept cross-reference map

> Builds incrementally as topics are covered. Shows how concepts connect — turns isolated facts into a navigable mental model.

*(Empty — will populate as DeepDive notes accumulate.)*

**Planned connections (preview):**
- AOP → JDK Proxy vs CGLib → `@Transactional` self-call trap
- IoC container → bean lifecycle → `@PostConstruct` → why init order matters
- DispatcherServlet → HandlerMapping → HandlerAdapter → `@Controller` method invocation
- `@SpringBootApplication` → `@EnableAutoConfiguration` → `spring.factories` (legacy) / `AutoConfiguration.imports` (modern)
- `@Transactional` → propagation modes → REQUIRED vs REQUIRES_NEW → real-world misuse

---

## 🔁 Session log (reverse chronological)

### Session 5 — 2026-05-18 (Day 2 written + practice code built + end-to-end verified)

**What was done:**
- Wrote `DeepDive/02-servlet-api.md` (~430 lines) covering:
  - The 5-method `Servlet` contract, with simplified container pseudocode showing instantiation + `init` + `service` threading + `destroy`
  - `HttpServlet.service()` dispatch — simplified actual source showing the method-based switch into `doGet`/`doPost`/etc.
  - Lifecycle ASCII timeline with KEY INVARIANT (one instance × many threads × concurrent `service`)
  - `HttpServletRequest` / `HttpServletResponse` raw API → Spring annotation maps (both directions)
  - Three registration mechanisms (web.xml → `@WebServlet` → `ServletContainerInitializer` SPI), with Spring's choice (#3) called out
  - Embedded vs standalone container history — why embedded won (dev parity, single artifact, cloud-native fit)
  - Five common-bugs callouts: instance fields, response not flushed, headers after body, missing `super.init(config)`, leaked DB conn on destroy
  - "Lesson learned the hard way (May 2026)" callout on `ArrayList` in a servlet instance field
  - 🎤 5-question Q&A appendix with model answers
- Wrote `Reference/02-servlet-api-reference.md` (~225 lines) — all 8 required sections including 14 interview one-liners + 60-second mental rehearsal script
- Built `Practice/exercises/01-servlet-hello/` — full Maven project, `@WebServlet`-annotated `HelloServlet` on embedded Jetty 11, with:
  - `AtomicInteger` counter to demonstrate the thread-safe instance-field pattern
  - `X-Request-Count` response header to prove status/headers-before-body ordering
  - Detailed README with 3 "now break it" exercises (race the counter / write body before header / try web.xml registration)
- Built `Practice/growing-app/v1-servlet/` — the v1 of the growing app, `GET /orders/{id}`:
  - Hand-rolled JSON serialization on `Order` record (the `toJson()` method that v2's Jackson will replace)
  - Manual URL parsing via `req.getPathInfo()` + `split("/")` (what `@PathVariable` will replace)
  - Hand-rolled error response helper (what `@ExceptionHandler` will replace)
  - README explicitly previews the v2 controller form (`@GetMapping("/{id}")` + `@PathVariable`) so the line-count comparison is concrete now, not just promised for Day 6
- **End-to-end verified both projects:**
  - `mvn compile` on each — clean (downloaded deps from corporate Maven mirror)
  - `mvn jetty:run` on each — Jetty 11 booted on :8080, served all happy/error paths correctly
  - Concurrency test on `01-servlet-hello`: fired 10 parallel curls, verified `X-Request-Count` was 4–13 with no duplicates (proving `AtomicInteger` raced correctly)
  - Growing-app v1: verified 200 happy path, 404 unknown-id, 400 missing-id, 400 wrong-shape, 405 wrong-verb (the last via `HttpServlet`'s default `doPost`)

**Why this matters:**
- Day 2 is the **last day of "raw" infrastructure** — every day after this introduces a Spring abstraction over what was just built. The v1 servlet is the *baseline* against which v2 (Spring MVC) and v3 (Spring Boot) will be compared. Without v1 being concrete and runnable, the Day 7 "look at the line-count delta" lesson loses its punch.
- The exercise README's "now break it" section is the active-learning piece — Kapil will see the counter race in real time and remember it.
- End-to-end verification matters because Maven dep resolution on the corporate network is the most common silent setup failure. Both projects pulling cleanly today means Day 3+ can rely on the same toolchain.

**Decisions made this session:**
- **Jakarta servlet (5.0+) + Jetty 11** chosen over javax/Jetty 9 — modern stack; matches what Spring Boot 3 uses. (Spring Boot 2.x and older mcse_lite use `javax.servlet.*` — we'll note this contrast on Day 6.)
- **No Jackson in v1's pom** — the "manual JSON" lesson is the v1 deliverable. v2 will be the first place Jackson appears.
- **Each Practice project is self-contained** (separate `pom.xml`, separate Jetty plugin invocation) — so they can be run independently without cross-talk. Matches the Practice README's stated convention.
- Annotation-based `@WebServlet` registration chosen as the primary path (modern Servlet 3.0+); the README walks through the equivalent web.xml registration as a "compare with the old way" exercise.

**Open questions for Kapil:**
- Run `mvn jetty:run` in `exercises/01-servlet-hello/` and try the 4 curl scenarios in its README. Did the lifecycle + thread model click in practice the way it did on paper?
- Then run the 3 "now break it" exercises in sequence. Did seeing the counter race / missing header / web.xml swap make any concept land harder than the DeepDive prose did?
- Run `mvn jetty:run` in `growing-app/v1-servlet/`. Count the lines of code in `OrderServlet.java`. Hold that number in your head — Day 6 you'll see v2 do the same job in ~5 lines of method body.
- Anything to log in Section 7 (bug/lesson log)?

**Next:** Confirm Day 2 is DONE → proceed to Day 3 (IoC + DI part 1: the container, ApplicationContext, bean discovery)

---

### Session 4 — 2026-05-18 (Reference-per-day formalized; Day 1 Reference written)

**What was done:**
- Kapil raised a key gap: the DeepDive is great for *learning* but useless for *revising* the night before an interview (1380 lines is too long to skim under time pressure)
- **Established the per-day pattern: DeepDive + Reference, written in that order**
  - DeepDive (400–1500 lines) = learning, day-of study session
  - Reference (200–400 lines) = revising, night-before-interview + during-day refresh
  - Reference is written AFTER the DeepDive is complete, distilling its content; **never adds new concepts**
- Created `Reference/01-web-fundamentals-reference.md` — ~350 lines, all tables + bullets + one-liners + a **60-second mental rehearsal script** that walks the entire request lifecycle in spoken-aloud form for the morning of the interview
- Required Reference structure formalized in `spring-10-hour-plan.md` → "🎤 Per-day note format" with 8 mandatory sections (one-line hook, killer diagram, cheat tables, common bugs one-liners, debugging instincts, interview one-liners, 60-sec rehearsal, companion links)
- Updated `spring-10-hour-plan.md`:
  - Added the DeepDive vs Reference comparison table at the top of "Per-day note format"
  - Added a `**Deliverables:**` line to every Day's entry listing both the DeepDive file AND the Reference file
  - Reworked Day 10's activities — References are now produced per-day, so Day 10's job is the **master index Reference** (`Reference/00-master-index.md`) + the cross-topic annotations cheatsheet (`Reference/spring-annotations-cheatsheet.md`), not building cheatsheets from scratch
- Updated this prep-log:
  - Status table now has a separate **Reference** column alongside DeepDive
  - Day 1 Reference marked ✅
  - Per-day deliverable rule noted under the status legend

**Why this matters:**
- Two-file pattern matches the DSA folder pattern (`DSA/DeepDive/` + `DSA/Reference/`) — consistency across the knowledge base
- Reference is the actual interview-prep artifact; the DeepDive is the one-time learning artifact. Optimizing them separately means each can serve its purpose without compromise.
- The **60-second mental rehearsal** section is the killer feature: a recitable script that takes the entire absorbed mental model and turns it into rehearsable speech. Reading it aloud daily until interview-day = the actual practice for "tell me what happens behind `@RestController`."

**Decisions made this session:**
- Reference files use the universal `🔹` emoji for major H2 sub-sections (per `../../AGENTS.md`)
- Day 10's "build cheatsheets" activity reworked into "build master index + cross-topic annotations cheatsheet" since per-day References will already exist
- File naming: `Reference/0X-topic-reference.md` (consistent with DSA pattern of `0X-topic.md` files)

**Open questions for Kapil:**
- Read `Reference/01-web-fundamentals-reference.md` end-to-end (5-10 min). Does it feel like a usable revision artifact? Anything missing? Anything redundant?
- Specifically: does the **60-second mental rehearsal** at the bottom feel recitable? Or does it need to be shorter / different cadence?
- Should the annotations cheatsheet (Day 10 deliverable) be built incrementally — appending each new annotation to it as each Day completes — instead of all at once on Day 10?

**Next:** Confirm Day 1 (DeepDive + Reference) is DONE → proceed to Day 2 (Servlet API + hello-world + v1 of growing app + Day 2 Reference)

---

### Session 3 — 2026-05-18 (Day 1 deepened — second-hour elaboration woven in place)

**What was done:**
- Kapil requested *"one more hour of elaboration on web fundamentals"* to make the mental model rich enough to *imagine* what happens behind a Spring Boot request
- Explicit instruction from Kapil: **"edit the same doc instead of adding at the end"** — so the second hour's material is woven IN PLACE at topically relevant locations, not bolted on as a Part 3
- Eight targeted in-place edits to `DeepDive/01-web-fundamentals.md`:
  1. **Layer 1** — added HTTP methods table (verb semantics: safe/idempotent), 5 status-code families table, "headers worth knowing by heart" table (Host, Content-Type, Content-Length, Transfer-Encoding, Accept, Authorization, Cookie, Connection, X-Forwarded-For)
  2. **Layer 2** — added port ranges (well-known / registered / ephemeral), "a socket = a file descriptor" with file-descriptor table, "Address already in use — what's really happening" troubleshooting table, `SO_REUSEADDR` callout
  3. **Layer 3** — added "What does parse HTTP actually mean?" with 4-step English plan + conceptual parser code, "WAR files vs embedded containers" history table contrasting classical (mcse_lite) vs Spring Boot eras
  4. **Thread model** — added "what 'the thread is blocked' actually means at the OS level" (RUNNABLE → WAITING, 1MB stack per thread), preview of **Servlet 3.0+ async** with `request.startAsync()` 4-step pattern and code skeleton, Spring's `CompletableFuture`/`DeferredResult`/`Callable` bridge
  5. **Part 2 Stage 2 (TCP handshake)** — added the **4-tuple** explanation (client IP + client port + server IP + server port), foreshadowing why `TIME_WAIT` matters
  6. **Part 2 Stage 6 (close/keep-alive)** — added `TIME_WAIT` deep-dive with the ephemeral port exhaustion failure mode (the "intermittent connection refused" production bug)
  7. **Part 2 Section 2 (HttpServletRequest dissected)** — added a **fully worked end-to-end trace**: a concrete `curl -X POST /orders/123?source=web` with all headers/cookies/body → the exact raw HTTP bytes → a Spring controller with 6 different `@`-annotated parameters → step-by-step explanation of where each one came from in the raw bytes
  8. **Part 2 Section 3 (filter chain)** — added "How do filters get into the chain?" with the three registration mechanisms (web.xml, `@WebFilter`, `FilterRegistrationBean`) + code example showing ordering, plus the **TL;DR cheatsheet** got a "Five debugging instincts" section

**Why this matters:**
- The user explicitly framed this as worth investing in: *"these are the building blocks."* The mental model now covers protocol semantics (status codes, methods, headers), OS-level identity of connections (file descriptors, ports, 4-tuple), parsing internals, deployment-model history (WAR vs embedded), thread-state mechanics at the kernel level, async escape hatch, and a concrete worked trace from `curl` bytes to bound `@`-annotated parameters
- After this round, Kapil should be able to mentally narrate: *"my Spring app gets a request → here's the 4-tuple identifying its socket → here's the worker thread that picks it up → here's why my `@RequestBody` is null when `Content-Type` is wrong → here's why I see `Connection refused` outbound during a load test."*
- File grew from ~1030 → ~1380 lines, but every addition is at a topically relevant location (no new appendix at the end)

**Decisions made this session:**
- **Editing-in-place is the right pattern** for "elaborate further on the same topic" — beats appending Parts 3/4/5 because the doc stays narratively coherent and re-readable top-to-bottom
- Will use the same pattern when Day 2 needs deepening based on Kapil's feedback after he works through it

**Open questions for Kapil:**
- Did the depth feel sufficient this time? Specifically: can you now close your eyes and *picture* the 4-tuple of a connection, the worker thread state-transitioning through RUNNABLE/WAITING, the parsed `HttpServletRequest` getting fed into each `@`-annotated controller parameter?
- Any specific area that still feels thin (TLS internals? HTTP/2? cookies/sessions? CORS?)
- Anything to log in Section 7 (bug/lesson log)?

**Next:** Confirm Day 1 is DONE → proceed to Day 2 (Servlet API + hello-world + v1 of growing app)

---

### Session 2 — 2026-05-18 (Day 1 written)

**What was done:**
- Pulled real `@RestController` snippet from `mcse_lite/.../PromiseImperiumServiceController.java` for the "in your app" callout
- Wrote `DeepDive/01-web-fundamentals.md` — full Day 1 note covering:
  - Four-layer mental model (TCP → HTTP text → servlet container → servlet)
  - Hand-written naive Java server (conceptual, shows what containers automate away)
  - Servlet API basics (`init`/`service`/`destroy`, `HttpServlet.doGet`)
  - **Thread model + singleton gotcha** (no mutable instance fields in servlets OR `@RestController`s)
  - ASCII diagram of the full request journey (Picture + Invariant)
  - 5-min `nc -l 8080` + `curl -v` hands-on (zero-Java way to see HTTP as text)
  - 5-row "common mistakes" table (JVM ≠ server, Boot uses Tomcat, etc.)
  - 4-question 🎤 Interview Q&A appendix with model answers
  - 🐞 Lesson-learned callout dated 2026-05-18 on the JVM/container/framework conflation
  - Production code snippet (eg, in your app) from `PromiseImperiumServiceController` lines 17-25 with attribution
- Updated this prep-log: Day 1 status DRAFT, "what's next" pointing at Day 2

**Decisions made this session:**
- Day 1 = pure reading + optional 5-min `nc` hands-on (didn't force any Java code on Day 1; Day 2 has the first servlet)
- Q&A appendix format: 4 questions per DeepDive (calibrated for the 1-hr study slot)

**Open questions for Kapil:**
- Did the four-layer mental model click, or did one of the layers (TCP, container, servlet) feel underexplained?
- Did the `nc -l 8080` hands-on land? (If you ran it, did seeing raw HTTP text actually shift the mental model?)
- Anything to log in Section 7 (bug/lesson log)?

**Next:** Day 2 — Servlet API + hello-world + v1 of growing app

---

### Session 1 — 2026-05-18 (setup)

**What was done:**
- Locked Path A (10-hour Tier-1 plan)
- Confirmed file structure for the Spring sub-track
- Augmented `JavaBackend/AGENTS.md` with the "Track-Style Sub-Folders" section
- Created this prep-log file
- Created `spring-10-hour-plan.md` (master plan with day-by-day breakdown)
- Created `Practice/README.md` (Maven + JDK setup, growing-app structure)
- Surveyed `mcse_lite` for Spring annotation usage — confirmed it's classical Spring (not Boot), has `@Aspect`, `@KafkaListener`, plenty of `@Controller`/`@Service`/`@Configuration`

**Decisions made this session:**
- Spring track gets its own sub-folder with self-contained prep-log + plan + Practice
- In-your-app code references will use `mcse_lite` (verbatim snippets, attributed by file:line)
- Three custom inclusions confirmed: Q&A appendix, in-your-app callouts, bug log

**Open questions:** none — ready to start Day 1 next session

**Next:** Day 1 — write `DeepDive/01-web-fundamentals.md`

---

### Changelog (of THIS prep-log file)

| Date | Change |
| --- | --- |
| 2026-05-18 | Bootstrapped. Sections 1-8 populated. Day 1 set as next. |
| 2026-05-18 | Day 1 DRAFT — `01-web-fundamentals.md` written (4-layer model, 5-min `nc` hands-on, production code snippet, Q&A appendix). Section 6 advanced to Day 2. |
| 2026-05-18 | Day 1 deepened (Session 3). Eight in-place edits across Parts 1 + 2: HTTP methods/status/headers, ports/sockets/file descriptors, HTTP parser, WAR vs embedded history, async servlet preview, 4-tuple, TIME_WAIT, worked end-to-end trace, filter registration mechanics. File now ~1380 lines. **Established the "edit in place, don't append" pattern for deepening passes.** |
| 2026-05-18 | **Reference-per-day pattern formalized (Session 4).** New rule: every day produces a DeepDive + a Reference (distilled cheatsheet, ~200-400 lines, written after DeepDive is complete). Required Reference structure documented in `spring-10-hour-plan.md`. Day 1 Reference (`Reference/01-web-fundamentals-reference.md`) written — includes a 60-second mental rehearsal script for interview-morning recitation. Status table gained a Reference column. |
| 2026-05-18 | **Day 2 DRAFT (Session 5).** `DeepDive/02-servlet-api.md` (~430 lines, 5 parts + 5 bug callouts + 5-question Q&A) and `Reference/02-servlet-api-reference.md` (~225 lines, 14 interview one-liners + 60-sec rehearsal) written. Two Maven/Jetty projects built and verified end-to-end: `exercises/01-servlet-hello/` (hello-world with `AtomicInteger` + lifecycle logging, 3 "now break it" exercises) and `growing-app/v1-servlet/` (raw-servlet baseline of `GET /orders/{id}` with hand-rolled JSON and manual URL parsing — the comparison baseline for Day 6 v2 and Day 7 v3). Both compile cleanly and serve correctly on Jetty 11 + Jakarta servlet 5.0. Section 6 advanced to Day 3. |
| June 2026 | **10-day plan restructured to 4-chapter track.** Original `01-web-fundamentals.md` + `02-servlet-api.md` merged into `DeepDive/01-web-servlet-foundation.md` (Chapter 1, ✅ complete — includes worked trace, three servlet scopes, async servlet preview that were present in the originals). `DeepDive/02-spring-core.md` written as Chapter 2, covering Days 3+4+5 (IoC/DI, bean lifecycle, AOP, `@Transactional` self-call trap). Chapter 3 (`03-spring-mvc-boot.md`) covers Days 6+7+8; Chapter 4 (`04-jpa-transactions.md`) covers Days 9+10. Status table and all cross-references updated to reflect the 4-chapter structure. Reference files follow the same chapter numbering. |
