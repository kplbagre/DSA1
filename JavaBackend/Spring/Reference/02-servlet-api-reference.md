# Day 2 — Servlet API · Reference cheatsheet

> **Companion to:** `../DeepDive/02-servlet-api.md`. **Read this:** the night before the interview + during the day to refresh. Distilled from the DeepDive — adds no new concepts.

---

## ⚡ One-line mental hook

> **A servlet is one shared object that the container calls concurrently — `init()` once, `service()` many times in parallel, `destroy()` once. `HttpServletRequest`/`Response` is the raw API; every Spring annotation is a typed wrapper on top.**

---

## 🔹 The killer diagram

```
                          ┌──────────────────────────────────┐
                          │   Servlet container (Tomcat/Jetty)│
HTTP socket  ──parse──►   │                                   │
                          │       worker thread pool          │
                          │           │  │  │   (200 threads) │
                          │           ▼  ▼  ▼                 │
                          │   ┌─────────────────────────┐     │
                          │   │  ONE servlet instance    │    │
                          │   │  service(req, res)       │    │
                          │   │    └─ doGet / doPost ... │    │
                          │   └─────────────────────────┘     │
                          │           │                       │
                          └───────────┼───────────────────────┘
                                      ▼
                                HTTP response

INVARIANT:
   1 instance × N threads = instance fields are shared mutable state.
   init() before everything; destroy() after everything; service() concurrent in between.
```

---

## 🔹 The 5-method Servlet contract

| Method | Calls | What it's for |
| --- | --- | --- |
| `init(ServletConfig)` | once, before any request | one-shot setup; read init params; open resources |
| `service(req, res)` | N times, concurrently | the hot path; every request lands here |
| `destroy()` | once, at shutdown | release resources (best-effort, not guaranteed on crash) |
| `getServletConfig()` | rarely called | accessor for the init-time config object |
| `getServletInfo()` | rarely called | descriptive string (admin tools) |

**You override:** `init()` (no-arg version) + `doGet` / `doPost` / etc. Almost never anything else.

---

## 🔹 `HttpServlet.service()` dispatch (memorize)

```
service(ServletRequest, ServletResponse)        ← container calls THIS
    └─ downcast to HttpServletRequest/Response
        └─ service(HttpServletRequest, HttpServletResponse)
              ├─ if GET    → doGet
              ├─ if POST   → doPost
              ├─ if PUT    → doPut
              ├─ if DELETE → doDelete
              ├─ if HEAD   → doHead
              ├─ if OPTIONS → doOptions
              ├─ if TRACE  → doTrace
              └─ else      → 405 Method Not Allowed
```

**Spring's `DispatcherServlet` overrides `service(HttpServletRequest, HttpServletResponse)` directly** — bypasses the verb-based switch and uses its own `HandlerMapping`.

---

## 🔹 Lifecycle timeline

```
container start
   │
   ▼
new MyServlet()        ← reflective no-arg ctor (no config wired yet)
   │
   ▼
init(servletConfig)    ← ONCE, before any request
   │
   ▼
service(req, res)  service(req, res)  service(req, res)   ← concurrent, N times
   │
   ▼
destroy()              ← ONCE, at shutdown (not on crash!)
   │
   ▼
container shutdown
```

---

## 🔹 `HttpServletRequest` getters → Spring annotation map

| Raw API | Spring equivalent |
| --- | --- |
| `req.getMethod()` | `@GetMapping` / `@PostMapping` |
| `req.getRequestURI()` (path) | (auto from mapping) |
| path segments via `getPathInfo()` | `@PathVariable("id")` |
| `req.getParameter("q")` | `@RequestParam("q")` |
| `req.getParameterValues("tag")` | `@RequestParam List<String>` |
| `req.getHeader("X-Trace-Id")` | `@RequestHeader("X-Trace-Id")` |
| `req.getReader()` → Jackson | `@RequestBody MyDto` |
| `req.getCookies()` | `@CookieValue("name")` |
| `req.getSession()` | `@SessionAttribute` |

> **Mental model:** every Spring annotation = one of these calls + type conversion + null/default handling.

---

## 🔹 `HttpServletResponse` setters → Spring equivalents

| Raw API | Spring equivalent |
| --- | --- |
| `res.setStatus(201)` | `ResponseEntity.status(201).build()` |
| `res.sendError(404)` | throw → `@ExceptionHandler` |
| `res.setHeader(k, v)` | `ResponseEntity.header(k, v)` |
| `res.setContentType("application/json")` | `@RequestMapping(produces=...)` |
| `res.getWriter().write(json)` | `return obj;` (Jackson via `@ResponseBody`) |
| `res.sendRedirect(url)` | `return "redirect:/url"` |

**Critical ordering rule:** set status + headers + content type **BEFORE** writing any body bytes. Once the first byte goes out, the response is *committed* — `setHeader` etc. are silently ignored.

---

## 🔹 Registration mechanisms (oldest → newest)

| Mechanism | Servlet ver | How |
| --- | --- | --- |
| `web.xml` | 2.x | `<servlet>` + `<servlet-mapping>` in `WEB-INF/web.xml` |
| `@WebServlet` annotation | 3.0+ | `@WebServlet(urlPatterns = "/foo")` on the class; container scans classpath |
| `ServletContainerInitializer` | 3.0+ | programmatic `ctx.addServlet(...)` at boot; registered via SPI file in `META-INF/services/` |

**Modern Spring uses #3.** Spring ships `SpringServletContainerInitializer`. Spring Boot collapses it further into a `@Bean DispatcherServlet` registered to embedded Tomcat at `/`.

**URL pattern types:**
| Pattern | Match |
| --- | --- |
| `/hello` | exact |
| `/api/*` | prefix |
| `*.jsp` | extension |
| `/` | default (catch-all) |

---

## 🔹 Embedded vs standalone container

| | Standalone (legacy) | Embedded (modern) |
| --- | --- | --- |
| Deliverable | `.war` file dropped into Tomcat's `webapps/` | fat `.jar`, `java -jar app.jar` |
| Who starts the container | external Tomcat service | your `main()` |
| Dev / prod parity | depends on installed Tomcat version | bundled (same version everywhere) |
| Cloud-native fit | awkward — one container hosts many apps | natural — one process = one pod |
| Spring Boot uses | NO | YES (starter pulls in `tomcat-embed-core`) |

---

## 🔹 Common bugs — one-liner each

| Bug | Cause | Fix |
| --- | --- | --- |
| Counter undercounts under load | `int requestCount` instance field, not atomic | `AtomicInteger` (or push to metrics lib) |
| Response body empty | forgot `getWriter().flush()` (or close) | try-with-resources or explicit `flush()` |
| Custom header missing | called `setHeader` after writing body | set headers / status BEFORE writing |
| `getInitParameter` returns null | overrode `init(ServletConfig)` without `super.init(config)` | call `super.init(config)` first — or override no-arg `init()` |
| DB conn leak on redeploy | resource opened in `init()`, never closed | implement `destroy()` (and pool it properly) |
| `synchronized doGet` tanks throughput | serializes all 200 threads on one method | use atomic types or scope state out of the servlet |
| `@WebServlet` not picked up | container scanning disabled | check `<web-app metadata-complete="false">` or use `web.xml` registration |
| `405 Method Not Allowed` | `HttpServlet.doX` default returns 405 — you didn't override the right verb | override `doPost` (or whichever) explicitly |

---

## 🔹 Debugging instincts

| Symptom | First suspect |
| --- | --- |
| empty response body, no error | missed `flush()` / `close()` on writer |
| missing custom response header | wrote body before setting header (committed) |
| race condition / corrupted state | instance field mutated from `service()` |
| `getInitParameter` returns null | missing `super.init(config)` |
| `405 Method Not Allowed` | wrong `doX` method (or none) overridden |
| servlet never starts despite registration | `init()` threw → check container startup log |
| throughput collapses under load | servlet has `synchronized` somewhere on a hot path |
| 404 on a URL you registered | URL pattern mismatch (`/foo` vs `/foo/*`); check exact `<url-pattern>` |

---

## 🎤 Interview one-liners (rattle-off form)

- **"What is a servlet?"** → "A Java class that implements `Servlet.service(req, res)` — the container creates one instance and calls `service()` concurrently from a thread pool, once per HTTP request."

- **"What does `HttpServlet.service()` do?"** → "It downcasts to `HttpServletRequest/Response`, inspects `req.getMethod()`, and dispatches to `doGet` / `doPost` / etc. — a built-in method-based switch."

- **"Lifecycle of a servlet?"** → "`init()` once before any request; `service()` N times concurrently across worker threads; `destroy()` once at shutdown — and not guaranteed on crash."

- **"Why are servlet instance fields dangerous?"** → "One servlet instance, many threads. Any instance field is shared mutable state across all concurrent requests — race conditions unless explicitly synchronized."

- **"Difference between constructor and `init()`?"** → "Constructor runs before any container wiring — no `ServletConfig`, useless for setup. `init()` runs after, with access to init params, `ServletContext`, etc. Always override `init`."

- **"What's a `ServletConfig`?"** → "Per-servlet config the container hands to `init()` — init params, the servlet's name, and a reference to the `ServletContext`."

- **"`ServletConfig` vs `ServletContext`?"** → "Config is per-servlet (init params for that servlet). Context is per-web-app (shared across all servlets in the same app)."

- **"What's `web.xml`?"** → "The original (Servlet 2.x) XML descriptor for declaring servlets and URL mappings. Modern apps use `@WebServlet` or programmatic registration instead."

- **"How does Spring register `DispatcherServlet`?"** → "Programmatically via `WebApplicationInitializer` → `ServletContainerInitializer` SPI. Spring Boot makes it a `@Bean` and the embedded container picks it up."

- **"Why does Spring Boot use an embedded container?"** → "Dev / prod parity (same Tomcat version everywhere), single deployment artifact (one fat JAR), and a clean one-process-per-pod fit for containers."

- **"What does `load-on-startup` do?"** → "Tells the container to call `init()` at boot, not lazily on first request. Useful for slow `init()` work like cache warming."

- **"What happens if you write body bytes before setting a header?"** → "Header is silently dropped — response gets *committed* at first byte, after which `setHeader` does nothing. Always set headers/status first."

- **"Why is `synchronized doGet` a bad idea?"** → "Serializes all 200 worker threads onto one instance. Throughput drops to the inverse of average latency. Make state thread-safe instead — `AtomicX`, `ConcurrentMap`, or move state out of the servlet."

- **"Where does the JSON serialization in `@RestController` happen?"** → "`HttpMessageConverter` (Jackson by default) runs inside Spring's `RequestMappingHandlerAdapter`, between the controller's return value and `res.getWriter()`. It's all still going to `res.getWriter().write(json)` underneath."

---

## 🧾 60-second mental rehearsal (recite the morning of the interview)

> *"A servlet is just a Java class implementing one method: `service(req, res)`. The container — Tomcat or Jetty — creates ONE instance of that class. When an HTTP request arrives on a socket, the container parses it into an `HttpServletRequest`, grabs a thread from its worker pool, and calls `service()` on the shared instance. With Tomcat's default pool of 200 threads, that means up to 200 concurrent calls into the same servlet object — so instance fields are shared mutable state.*
>
> *`HttpServlet` is a convenience class — its `service()` dispatches to `doGet`, `doPost`, etc., based on `req.getMethod()`. You override the verb-specific methods. Inside, you read inputs from `HttpServletRequest` — params, headers, body — and write outputs to `HttpServletResponse` — status, headers, body. Critical rule: status and headers must be set before the body is written; once a byte goes out, the response is committed.*
>
> *The lifecycle has three phases. `init()` runs once before any request — that's where you do setup. `service()` runs many times concurrently. `destroy()` runs once at shutdown — best-effort, not guaranteed on crash.*
>
> *Registration historically lived in `web.xml`. Servlet 3.0 added `@WebServlet` for annotation-based registration, and `ServletContainerInitializer` for programmatic registration via an SPI mechanism. Modern Spring uses the programmatic path: Spring's initializer registers `DispatcherServlet` at the catch-all `/` URL pattern. Spring Boot collapses this further — `DispatcherServlet` is just a `@Bean`, and the embedded Tomcat picks it up.*
>
> *Embedded containers replaced the WAR-deployment model around 2014. Same Tomcat, but now it's a library you start from your own `main()` — meaning one process per pod, no separate container install, and exact dev/prod version parity.*
>
> *So when I write `@RestController`, here's the picture: there's ONE `DispatcherServlet` instance running inside an embedded Tomcat. Every request lands in `DispatcherServlet.service()`. It uses `HandlerMapping` to find my `@Controller` method, invokes it via `HandlerAdapter`, and serializes my return value through Jackson back into `res.getWriter()`. Every Spring annotation I use — `@PathVariable`, `@RequestParam`, `@RequestBody` — is a typed wrapper around a getter on `HttpServletRequest`. The whole thing is sugar on top of `service(req, res)`."*

---

## 📚 Companion links

- **DeepDive (full mental model):** `../DeepDive/02-servlet-api.md`
- **Plan entry:** `../spring-10-hour-plan.md` § Day 2
- **Prep log:** `../spring-prep-log.md`
- **Previous reference:** `01-web-fundamentals-reference.md`
- **Next reference:** `03-ioc-di-container-reference.md` (TBD, Day 3)
- **Practice code:**
  - `../Practice/exercises/01-servlet-hello/`
  - `../Practice/growing-app/v1-servlet/`

---

### Changelog

| Date | Change |
| --- | --- |
| 2026-05-18 | Day 2 Reference written. Eight required sections. 14 interview one-liners + 60-second rehearsal script. |
