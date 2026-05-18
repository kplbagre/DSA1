# Spring Foundation — 10-Hour Master Plan

> **Companion file:** `spring-prep-log.md` (status, session log, bug log). Read that first if resuming work.

---

## 🎯 Goal

Build an **interview-ready mental model of Spring core + Spring Boot**, in 10 hours over 10 days, with hands-on practice that reinforces every concept. Optimize for *verbalizing the WHY* — not memorizing annotation syntax.

**What you'll be able to do after 10 days:**

1. Explain IoC, DI, and the bean lifecycle in 60 seconds without looking at notes
2. Articulate **why** `@Transactional` on a self-call doesn't work — and the proxy mechanism behind it
3. Trace a request from URL → `DispatcherServlet` → `@Controller` method → response, naming every component
4. Decompose `@SpringBootApplication` into its three constituent annotations and explain what each does
5. Explain JPA transaction propagation modes (REQUIRED vs REQUIRES_NEW) with real examples
6. Walk through three iterations of the same endpoint (raw servlet → Spring MVC → Spring Boot) and explain what each abstraction layer added

---

## 📋 Scope (Path A — locked)

### ✅ IN scope (Tier 1)

| Topic | Days | Why it's in Tier 1 |
| --- | --- | --- |
| Web fundamentals + Servlet API | 1-2 | Foundation for everything; `DispatcherServlet` IS a servlet |
| IoC + DI + bean lifecycle | 3-4 | The single most-asked Spring topic in interviews |
| AOP + proxies | 5 | Powers `@Transactional`, `@Async`, `@Cacheable`, security — interviewers love the self-call trap |
| Spring MVC internals | 6 | Bridges servlets and Spring Boot; explains request flow |
| Spring Boot auto-config | 7-8 | What `@SpringBootApplication` actually does + properties/profiles |
| Spring Data JPA + transactions | 9 | Persistence layer — interview probes propagation + lazy loading |
| Mock-interview consolidation | 10 | Verbalization practice — the actual skill being tested |

### ⏸️ DEFERRED to Tier 2 (post-interview)

- **Spring Security** — auth, filters, OAuth, JWT
- **Spring Cloud** — Feign, Eureka, Config Server, Resilience4j
- **Kafka + Spring** — `@KafkaListener`, error handling, partitioning
- **Advanced testing** — `@MockBean` depth, slice tests, Testcontainers

> **Why deferred:** Interviewers usually probe these as *"have you used X at work?"* — answerable from your work experience without internals depth. Tier-1 topics are where they grill on internals.

---

## 🧠 Why Path A specifically — pedagogical reasoning

**The progression is intentional and irreversible:**

```
Day 1-2   Web fundamentals  →  what a servlet IS
Day 3-4   IoC + DI          →  HOW Spring wires objects (DispatcherServlet is a wired bean)
Day 5     AOP               →  HOW Spring intercepts calls (used by @Transactional below)
Day 6     Spring MVC        →  Spring's servlet that uses DI + AOP to route requests
Day 7-8   Spring Boot       →  Auto-configures Spring MVC + AOP + DI
Day 9     Spring Data JPA   →  Uses @Transactional (Day 5) + Spring Boot starters (Day 7)
Day 10    Mock interview    →  Verbalize the whole stack
```

Skipping forward (e.g., starting at Spring Boot) means every annotation is magic. Going through this order means each new annotation has a known mechanism underneath.

---

## 🗺️ The 10-day arc (day-by-day breakdown)

Each day's DeepDive note follows this structure:
1. 📖 **Prerequisite line** (what to have absorbed before this)
2. 🧠 **Mental model** (one paragraph: the core insight)
3. 🪜 **Concept build-up** (English first, then code)
4. ❌/✅ **Common mistakes in comments** (the wrong-way-and-why pattern)
5. 🎨 **Visual** (ASCII diagram + invariant where applicable)
6. 🏢 **Where you've seen this in your app** (mcse_lite snippet, attributed)
7. 🎤 **Interview Q&A** (2-4 questions + model answers)
8. 🧾 **TL;DR / mental hook** (one-line revision anchor)

---

### Day 1 — Web fundamentals (1 hr, reading-only)

**Mental model:** Before Spring, before Java, there was just *HTTP*. A web server listens on a TCP socket, parses HTTP, and hands the request to "something that can run code." That "something" in Java-land is a **servlet**, and the runtime that hosts it is a **servlet container** (Tomcat/Jetty).

**Topics:**
- HTTP request/response in plain terms (method, headers, body, status)
- What a TCP socket is (briefly — just enough)
- Web server vs application server vs servlet container
- What a servlet IS (the contract — implement `Servlet` interface, container calls your `service()` method)
- Connection request → socket → thread → servlet → response

**Practice:** No code Day 1. Reading the DeepDive note + drawing the request lifecycle on paper.

**Codebase reference (eg, in your app):** Brief look at `mcse_lite` — note that every `@RestController` is ultimately a servlet wrapped by `DispatcherServlet`. We'll unpack this on Day 6.

**Self-check (the question to verbalize before sleeping):**
> "What does a servlet container do that a plain `main()` method can't?"

**Deliverables:** `DeepDive/01-web-fundamentals.md` + `Reference/01-web-fundamentals-reference.md` (Reference written immediately after DeepDive is complete)

---

### Day 2 — Servlet API + hello-world (1 hr, code time)

**Mental model:** A servlet is just `void doGet(req, resp)` (and friends). The container calls it. That's the whole API. Everything in Spring MVC is sugar on top of this.

**Topics:**
- `HttpServlet` lifecycle: `init()` → `service()` → `doGet`/`doPost`/etc. → `destroy()`
- `web.xml` — the original way to register a servlet at a URL
- Request parsing, response writing — raw API
- Embedded Jetty for zero-install testing

**Practice:**
- `exercises/01-servlet-hello/` — single servlet on `/hello` returning a string. Run with embedded Jetty via Maven.
- `growing-app/v1-servlet/` — **v1 of the growing app**: an `/orders/{id}` endpoint that returns hard-coded JSON. **Same endpoint will get rebuilt in Spring MVC (Day 6) and Spring Boot (Day 7).**

**Codebase reference (eg, in your app):** Grep `mcse_lite/.../web.xml` (if any) OR look at how `DispatcherServlet` is configured. Show that the controllers Kapil uses daily ARE servlets in disguise.

**Common mistakes shown in comments:**
- ❌ Forgetting that servlet methods are called concurrently → instance fields = race condition
- ❌ Forgetting to flush/close `resp.getWriter()` → empty responses in some containers

**Self-check:**
> "Trace what happens between a curl request and your `doGet` being called."

**Deliverables:** `DeepDive/02-servlet-api.md` + `Reference/02-servlet-api-reference.md` + `Practice/exercises/01-servlet-hello/` + `Practice/growing-app/v1-servlet/`

---

### Day 3 — IoC + DI part 1: the container (1 hr)

**Mental model:** Inversion of Control flips the question "who creates objects?" — instead of `new UserRepo()` inside `UserService`, an external container creates both and hands `UserRepo` to `UserService`. Spring's container is `ApplicationContext`. A *bean* is just a Java object whose lifecycle the container manages.

**Topics:**
- The IoC concept (don't use the word "inversion" in the explanation — that's the test)
- What "wiring" means
- `ApplicationContext` — the container API
- How beans get discovered (`@ComponentScan`, XML, `@Bean` methods)
- The 4 stereotype annotations: `@Component`, `@Service`, `@Repository`, `@Controller` (all = `@Component` underneath, semantic distinction only)

**Practice:**
- `exercises/02-bean-discovery/` — minimal app that scans a package, prints all registered beans. Use `AnnotationConfigApplicationContext`. No web layer yet.

**Codebase reference (eg, in your app):** Pick 2-3 `@Service` and `@Configuration` classes from `mcse_lite/components/*` and show how the container discovers them.

**Common mistakes:**
- ❌ Putting `@Service` on an interface (Spring doesn't scan it — needs to be on the impl class)
- ❌ `@Component`-scanning the wrong base package → beans not registered → cryptic `NoSuchBeanDefinitionException`

**Visual:** ASCII diagram of `ApplicationContext` discovering beans (component scan → BeanDefinition → instantiate → ApplicationContext map).

**Self-check:**
> "Explain Inversion of Control without using the word 'inversion' or 'IoC'."

**Deliverables:** `DeepDive/03-ioc-di-container.md` + `Reference/03-ioc-di-container-reference.md` + `Practice/exercises/02-bean-discovery/`

---

### Day 4 — IoC + DI part 2: DI types, scopes, lifecycle (1 hr)

**Mental model:** Once the container has beans, it has to *wire them together*. Three styles (constructor, setter, field). Then each bean lives somewhere on a *scope* (singleton, prototype, request, session). And the container calls hooks during creation/destruction — the *lifecycle*.

**Topics:**
- Constructor vs setter vs field injection (with **strong opinion: prefer constructor — and why**)
- Why field injection breaks tests (`@Autowired` field with no constructor → can't instantiate in unit tests without reflection)
- Bean scopes: `singleton` (default) vs `prototype` vs `request` vs `session`
- Lifecycle hooks: `@PostConstruct`, `InitializingBean.afterPropertiesSet`, `@Bean(initMethod=)`, mirror for destruction
- The "circular dependency" trap and how Spring resolves it (or doesn't, for constructor injection)

**Practice:**
- `exercises/03-bean-lifecycle/` — bean with logging in constructor, `@PostConstruct`, `@PreDestroy`. Run; observe call order. Switch scope to `prototype`; observe difference.

**Codebase reference (eg, in your app):** Look for `@PostConstruct` usage in `mcse_lite` and show the lifecycle hook in action.

**Common mistakes:**
- ❌ Field injection (`@Autowired private X x;`) — breaks immutability, hurts testability, hides dependencies
- ❌ Mixing singleton + prototype injection without `ObjectFactory` / `@Lookup` — the prototype gets injected ONCE, becomes effectively a singleton
- ❌ Doing heavy work in constructor instead of `@PostConstruct` — fails if dependencies aren't ready

**Visual:** ASCII timeline of bean lifecycle phases.

**Self-check:**
> "When does `@PostConstruct` fire — before or after `@Autowired` injection? Why?"

**Deliverables:** `DeepDive/04-bean-lifecycle.md` + `Reference/04-bean-lifecycle-reference.md` + `Practice/exercises/03-bean-lifecycle/`

---

### Day 5 — AOP + proxies + the `@Transactional` self-call trap (1 hr)

**Mental model:** Spring "intercepts" method calls by wrapping your bean in a *proxy*. When you call `userService.placeOrder(...)`, you're actually calling `userServiceProxy.placeOrder(...)` which does extra work (open transaction, log, check security) before delegating to your real method. **This breaks the moment you call a method on `this` — because `this` is your real object, not the proxy.**

**Topics:**
- The proxy pattern (1-minute refresher)
- JDK dynamic proxy (requires interface) vs CGLib subclass proxy (no interface needed)
- AOP terminology: aspect, advice, pointcut, joinpoint, advisor (just enough to recognize)
- `@Transactional` mechanics — what the proxy does on method entry/exit
- **The killer interview demo:** `@Transactional` on `methodA` called by `methodB` in the same class → NO transaction. Why. Fix.
- Other proxy-using annotations: `@Async`, `@Cacheable`, `@Scheduled`, `@PreAuthorize`

**Practice:**
- `exercises/04-aop-self-call-trap/` — `OrderService` with `@Transactional` `placeOrder()` and a `placeOrders(list)` that calls `this.placeOrder()`. Wire a `TransactionManager` with logging. Run; see that the batch call shows NO transaction. Fix using self-injection (`@Autowired private OrderService self`). Run again; see transactions.

**Codebase reference (eg, in your app):** `mcse_lite/core/.../SimulatorOrchestratorAspect.java` — real `@Aspect` in production code. Walk through what it intercepts and why.

**Common mistakes (this is the gold-mine day for the interview):**
- ❌ `this.method()` call to a `@Transactional` method from inside the same class → bypasses proxy → no transaction
- ❌ `@Transactional` on a `private` method → proxy can't intercept (JDK proxy only sees interface methods; CGLib can't subclass private)
- ❌ `@Transactional` on a `final` method (CGLib) → can't be overridden in the subclass proxy
- ❌ Forgetting that checked exceptions don't trigger rollback by default — only RuntimeException + Error do (have to specify `rollbackFor=Exception.class`)

**Visual:** ASCII picture of `caller → proxy → real bean` for external call vs `this → real bean directly` for internal call.

**Self-check:**
> "I have `placeOrder()` annotated `@Transactional`, and `placeOrderBatch()` in the same class calls it in a loop. Does each `placeOrder` get its own transaction? Why or why not? Fix."

**Deliverables:** `DeepDive/05-aop-and-proxies.md` + `Reference/05-aop-and-proxies-reference.md` + `Practice/exercises/04-aop-self-call-trap/`

---

### Day 6 — Spring MVC + the growing app v2 (1 hr)

**Mental model:** `DispatcherServlet` is a single servlet (registered with the container, just like Day 2's hello servlet). On every request, it looks up the right `@Controller` method via `HandlerMapping`, invokes it via `HandlerAdapter`, and renders the result. Everything in Spring MVC happens inside or around this one servlet.

**Topics:**
- The `DispatcherServlet` request lifecycle (the famous diagram, simplified)
- `HandlerMapping` (URL → method) and `HandlerAdapter` (how to invoke it)
- `@Controller` vs `@RestController` (the latter adds `@ResponseBody`)
- Method-level mappings: `@GetMapping`, `@PathVariable`, `@RequestParam`, `@RequestBody`
- View resolution vs message converters (Jackson for JSON)
- `@ControllerAdvice` and `@ExceptionHandler` (briefly)

**Practice:**
- `exercises/05-dispatcher-trace/` — minimal Spring MVC app (Java config, no Boot yet). Place a breakpoint or `log.info` in a controller, trace the call stack to see `DispatcherServlet.doDispatch` calling you.
- `growing-app/v2-spring-mvc/` — **v2 of the growing app**: rebuild the `/orders/{id}` endpoint using Spring MVC + `@Configuration` (no XML, no Boot). Run on embedded Tomcat or Jetty.

**Codebase reference (eg, in your app):** `mcse_lite/.../HollowCacheServiceController.java` and `PromiseImperiumServiceController.java` — real production Spring MVC controllers. Walk through their annotations.

**Common mistakes:**
- ❌ Forgetting `@ResponseBody` (or `@RestController`) → Spring tries to resolve a view named "orders/123" and you get a 404 or template error
- ❌ Wrong content-type → `@RequestBody` can't deserialize JSON if Content-Type is `application/x-www-form-urlencoded`
- ❌ Returning `ResponseEntity<Object>` but forgetting the generic — runtime serialization issues

**Visual:** ASCII flow of `Request → DispatcherServlet → HandlerMapping → HandlerInterceptor → HandlerAdapter → @Controller method → MessageConverter → Response`.

**Self-check:**
> "Trace a `GET /orders/123` from the TCP socket to the JSON response. Name every Spring component involved."

**Deliverables:** `DeepDive/06-spring-mvc.md` + `Reference/06-spring-mvc-reference.md` + `Practice/exercises/05-dispatcher-trace/` + `Practice/growing-app/v2-spring-mvc/`

---

### Day 7 — Spring Boot + the growing app v3 (1 hr)

**Mental model:** Spring Boot is **opinionated auto-wiring of Spring**. `@SpringBootApplication` = `@Configuration` + `@ComponentScan` + `@EnableAutoConfiguration`. The last one is the magic: it looks at classpath, sees `spring-webmvc`, and auto-configures `DispatcherServlet`, view resolver, message converters, etc. — all the things you wired BY HAND in Day 6.

**Topics:**
- `@SpringBootApplication` decomposed into its three constituent annotations
- Starters (`spring-boot-starter-web`, `-data-jpa`, etc.) — curated dependency bundles
- `@EnableAutoConfiguration` mechanism: scans `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (Boot 3.x; was `spring.factories` in 2.x)
- Embedded server (Tomcat default, configurable to Jetty/Undertow)
- `application.properties` / `application.yml` basics
- Actuator endpoints (briefly — `/health`, `/metrics`)

**Practice:**
- `exercises/06-autoconfig-peek/` — start a Boot app, enable debug logging, see ALL the auto-configurations applied. Run with `--debug`. Find the auto-configured `DispatcherServlet`.
- `growing-app/v3-spring-boot/` — **v3 of the growing app**: the same `/orders/{id}` endpoint, but in Spring Boot. **Compare v1, v2, v3 side-by-side** — pure code reduction is the lesson.

**Codebase reference (eg, in your app):** Important callout — `mcse_lite` is NOT Spring Boot. Show what the manual config looks like there, and contrast with what Boot's auto-config would generate.

**Common mistakes:**
- ❌ Putting `@SpringBootApplication` in a class deep in the package tree — `@ComponentScan` scans from THAT package downward, so beans in higher packages are missed
- ❌ Treating auto-config as magic and not knowing how to override it (next day's topic)

**Visual:** ASCII picture: `@SpringBootApplication` exploding into its three components.

**Self-check:**
> "What does `@SpringBootApplication` actually do? Decompose it."

**Deliverables:** `DeepDive/07-spring-boot-autoconfig.md` + `Reference/07-spring-boot-autoconfig-reference.md` + `Practice/exercises/06-autoconfig-peek/` + `Practice/growing-app/v3-spring-boot/`

---

### Day 8 — Properties, profiles, conditional config (1 hr)

**Mental model:** Spring Boot's flexibility comes from two ideas: (1) externalize *what* into properties; (2) make auto-config *conditional* so it backs off when you provide your own bean. Profiles let you swap whole sets of properties/beans per environment.

**Topics:**
- `@Value` for single property injection (the simple way)
- `@ConfigurationProperties` for type-safe property binding (the right way for groups)
- `application.yml` vs `application.properties` — same thing, syntactic preference
- Profile activation: `spring.profiles.active=dev`, `application-dev.yml`
- `@Profile("dev")` on beans
- The conditional annotations: `@ConditionalOnMissingBean`, `@ConditionalOnClass`, `@ConditionalOnProperty` — how Boot's auto-config politely backs off when you do it yourself
- Property precedence order (env vars > command-line > profile-specific > application.yml)

**Practice:**
- `exercises/07-profiles-demo/` — two profiles (`dev`, `prod`) with different DB URLs and a profile-conditional bean. Run with each profile; observe which beans are wired.

**Codebase reference (eg, in your app):** Look at `mcse_lite` configs for profile-style switching (CCM2 properties / `@Value` usage).

**Common mistakes:**
- ❌ Using `@Value("${prop}")` for a property that doesn't exist → app starts then explodes at first use. Use `@Value("${prop:default}")` or `@ConfigurationProperties` with validation.
- ❌ Two beans of the same type, both `@Component` — `NoUniqueBeanDefinitionException`. Fix with `@Qualifier` or `@Primary`.

**Self-check:**
> "How would you override Spring Boot's auto-configured `DataSource` to use your own?"

**Deliverables:** `DeepDive/08-properties-and-profiles.md` + `Reference/08-properties-and-profiles-reference.md` + `Practice/exercises/07-profiles-demo/`

---

### Day 9 — Spring Data JPA + transactions + lazy loading (1 hr)

**Mental model:** JPA is the contract (Java standard); Hibernate is the implementation. Spring Data JPA wraps Hibernate to remove boilerplate — declare a `repository interface`, Spring generates the implementation. `@Transactional` (from Day 5's AOP) manages the persistence context and DB transaction together.

**Topics:**
- `@Entity` + `@Id` + relationships (`@OneToMany`, `@ManyToOne`)
- `JpaRepository<T, ID>` — methods you get for free
- Derived query methods (`findByOrderNumberAndStatus`) — naming convention magic
- `@Query` for custom JPQL
- Transaction propagation: `REQUIRED` (default — join existing or create new), `REQUIRES_NEW` (always new), `SUPPORTS`, `MANDATORY` — when each matters
- The **lazy-loading trap**: `LazyInitializationException` outside `@Transactional` context
- The N+1 query problem and `JOIN FETCH` as the fix

**Practice:**
- `exercises/08-jpa-lazy-loading/` — entity with `@OneToMany(fetch = LAZY)`. Fetch the parent in a `@Transactional` method, return it, then access children OUTSIDE → see the exception. Fix three ways (eager fetch / `JOIN FETCH` / `@Transactional` on the call site).

**Codebase reference (eg, in your app):** Any DAO patterns from `mcse_lite/components/dal-*/...`.

**Common mistakes:**
- ❌ Reading lazy collections outside a transaction → `LazyInitializationException`
- ❌ N+1 queries from iterating over a parent and accessing each child without `JOIN FETCH`
- ❌ Using `REQUIRED` when you actually wanted `REQUIRES_NEW` (e.g., audit logs should commit even if parent rolls back — but `REQUIRED` joins the parent's transaction and rolls back together)

**Visual:** ASCII timeline of REQUIRED vs REQUIRES_NEW with parent transaction.

**Self-check:**
> "Difference between `REQUIRED` and `REQUIRES_NEW`. Give a real example where each is correct."

**Deliverables:** `DeepDive/09-spring-data-jpa.md` + `Reference/09-spring-data-jpa-reference.md` + `Practice/exercises/08-jpa-lazy-loading/`

---

### Day 10 — Consolidation + mock interview self-test (1 hr)

**Goal:** Don't learn anything new. **Verbalize everything.** This day is about turning the absorbed mental model into spoken-aloud answers.

**Activities:**
1. **Walk all three growing-app versions side-by-side** (`v1-servlet`, `v2-spring-mvc`, `v3-spring-boot`). For each layer of abstraction added, explain out loud what was abstracted away.
2. **Self-administered mock interview** — recite every Reference note's **🎤 Interview one-liners** and **🧾 60-second mental rehearsal** aloud, without peeking. Compare gaps against the full DeepDive Q&A. Mark anything fuzzy.
3. **Build the master index Reference** (`Reference/00-master-index.md`) — a single page that links to all 9 day-Reference files, with the 60-sec rehearsal from each chained together as one ~10-minute "walk the whole stack" script.
4. **Cross-topic Reference** (`Reference/spring-annotations-cheatsheet.md`) — a flat alphabetical lookup of every annotation introduced (`@RestController`, `@PathVariable`, `@Transactional`, `@PostConstruct`, ...) with one-line semantics + which Day covered it. Built by pulling from each day's Reference, not duplicated.
5. **Update prep-log Section 8 (cross-reference map)** — explicit connections built over the 10 days.
6. **Plan Tier 2** (post-interview): Security, Cloud, Kafka — based on what came up at the actual interview.

**Self-check (the final one):**
> Imagine a 45-min interview. You're asked: *"Walk me through how Spring Boot starts an application and serves an HTTP request."* You should be able to talk for 8-10 minutes without stalling, naming every step.

**Deliverables:** `DeepDive/10-consolidation.md` (retrospective only — what clicked / what's still fuzzy) + `Reference/00-master-index.md` + `Reference/spring-annotations-cheatsheet.md`

---

## 🪜 The "growing app" — same problem, three iterations

Throughout the plan, you build **one tiny endpoint** (`GET /orders/{id}` → returns a hard-coded order JSON) **three times**:

| Version | Day | Tech | Lines of code (approx) | What it taught |
| --- | --- | --- | --- | --- |
| v1 | 2 | Raw servlet + embedded Jetty | ~50 lines + web.xml | What a servlet IS |
| v2 | 6 | Spring MVC + Java config (no Boot) | ~30 lines + 2 config files | What Spring MVC adds (DI, routing, message converters) |
| v3 | 7 | Spring Boot | ~10 lines | What auto-config adds |

**Why this works:** Same problem, three solutions, side-by-side comparison. You SEE what each abstraction layer removed. This is high-bandwidth conceptual learning.

---

## 🎤 Per-day note format

Every day delivers **two files** (plus practice code where applicable):

| File | Purpose | Length target | When to read |
| --- | --- | --- | --- |
| `DeepDive/0X-topic.md` | Learning — full mental model, examples, gotchas, worked traces | 400–1500 lines | **Day-of** study session |
| `Reference/0X-topic-reference.md` | Revising — tables, one-liners, the killer diagram, interview one-sentence answers, 60-sec mental rehearsal | 200–400 lines | **Night before interview** + during the day to refresh |

> **Why two files instead of one:** The DeepDive is too long to skim under time pressure; a 1380-line file isn't a revision aid. The Reference distills the DeepDive into something you can actually consume in 5–10 minutes. **The Reference is written AFTER the DeepDive is complete**, drawing only from material already present — never adds new concepts.

Every DeepDive note (`DeepDive/0X-topic.md`) ends with these three sections — they're how this plan delivers its three custom inclusions:

### 1. 🎤 Interview Q&A appendix

2-4 questions an interviewer might ask, with model answers. Speaking these out loud IS the interview-prep work.

### 2. 🏢 Where you've seen this in your app

A real `mcse_lite` snippet illustrating the concept, with file:line attribution. Anchors the concept in code Kapil already works with.

### 3. 🧾 TL;DR / mental hook

One sentence (sometimes two) that's the night-before-interview revision anchor.

### Reference note required sections

Every `Reference/0X-topic-reference.md` MUST contain:

1. **⚡ One-line mental hook** at the top (the killer sentence, lifted from DeepDive TL;DR)
2. **🔹 The killer diagram** — the one ASCII picture that anchors the mental model
3. **Cheat tables** — every table from the DeepDive, compressed, no prose
4. **🔹 Common bugs — one-liner each** — bug → cause → fix
5. **🔹 Debugging instincts** — symptom → first suspect
6. **🎤 Interview one-liners** — every Q from the DeepDive's Q&A, distilled to **one sentence** (rattle-off form, not the model-answer paragraph)
7. **🧾 60-second mental rehearsal** — a scripted verbal walkthrough you can recite to yourself the morning of the interview
8. **📚 Companion links** back to the DeepDive + plan + prep-log

> Use the universal emoji `🔹` for major H2 sub-sections in Reference notes (per `../../AGENTS.md`).

---

## 📚 Cross-references

- **DSA prep:** `../../DSA/interview-prep-17-day-plan.md` — runs in parallel; heavy DSA days = light Spring days
- **Reference codebase:** `/Users/k0b077v/new_mcse/mcse_lite` — all production code snippets pulled from here (eg, in your app)
- **Universal style rules:** `../../AGENTS.md`
- **JavaBackend subdomain rules:** `../AGENTS.md`
- **Status & session log:** `spring-prep-log.md` (live file)
- **Future tracks (post-Tier-1):** Spring Security, Spring Cloud, Kafka+Spring (paths TBD)

---

## ✂️ Cut criteria (if behind schedule)

If you're behind by Day 5, cut in this order:

1. **First to cut:** Day 8's `@ConditionalOn*` deep dive (covered briefly in Day 7 anyway)
2. **Second cut:** Day 9's transaction propagation modes beyond REQUIRED (cover REQUIRES_NEW; skip MANDATORY/SUPPORTS/NEVER)
3. **Never cut:** Day 5 (AOP self-call) — interviewer gold mine. Day 7 (auto-config) — `@SpringBootApplication` question is unavoidable.

---

## 💪 What you'll actually say in an interview after Day 10

Three sample one-liners you should be able to drop:

> *"`@Transactional` works via a Spring-generated proxy that wraps your bean. When you call a `@Transactional` method on `this` from inside the same class, you bypass the proxy — so no transaction. Fix is to inject self-reference or split the method into another bean."*

> *"`@SpringBootApplication` is just `@Configuration` + `@ComponentScan` + `@EnableAutoConfiguration`. The auto-config piece scans the classpath, sees which starters are present, and wires the appropriate beans — but it backs off whenever you've provided your own."*

> *"Spring MVC is one servlet — `DispatcherServlet` — that uses `HandlerMapping` to find the right `@Controller` method, `HandlerAdapter` to invoke it, and `HttpMessageConverter` to serialize the return value. Everything else in `@RestController` is sugar."*

These are the interview-day outputs of the 10-day plan.
