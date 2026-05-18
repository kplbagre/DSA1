# Day 1 Reference — Web Fundamentals Cheatsheet

> **Companion to `../DeepDive/01-web-fundamentals.md`** — that file is for learning; this file is for revising. Skim end-to-end in 5-10 minutes; the night before the interview, re-read sections **🧾**, **🎤**, and the 60-second mental rehearsal at the bottom.

---

## ⚡ The one-line mental hook

> **HTTP is text over TCP. A servlet container parses that text and calls your Java code. A servlet is the Java class it calls. Every `@RestController` is a servlet in disguise — Spring's `DispatcherServlet` is the actual servlet the container sees; your controllers are routed by it.**

---

## 🔹 The complete picture — one diagram

```
curl http://api.example.com/orders/123
            │
            ▼
[Stage 1] DNS         → IP lookup (api.example.com → 1.2.3.4)
[Stage 2] TCP         → 3-way handshake (SYN / SYN-ACK / ACK)
[Stage 3] TLS         → encrypted handshake (usually terminated at LB)
[Stage 4] HTTP req    → "GET /orders/123 HTTP/1.1\nHost: ...\n\n"
            │
            ▼
┌─ TOMCAT ─────────────────────────────────────────────────┐
│  • Acceptor thread → accept() returns socket             │
│  • Worker thread (1 of 200) parses HTTP → HttpServletReq │
│  • Filter chain: Encoding → Security → Logging → ...     │
│  • DispatcherServlet: HandlerMapping → HandlerAdapter    │
│  • Your @Controller method runs (BLOCKS on I/O)          │
│  • HttpMessageConverter (Jackson) serializes → JSON      │
│  • Filters' post-logic runs                              │
│  • Tomcat writes response bytes → socket                 │
└──────────────────────────────────────────────────────────┘
            │
            ▼
[Stage 5] HTTP resp   → "HTTP/1.1 200 OK\nContent-Type: ..."
[Stage 6] Close/reuse → HTTP/1.1 keep-alive by default

KEY INVARIANT:
   Every Spring Boot request flows through ALL of these layers.
   @GetMapping = the last step; everything above is the container's job.
```

---

## 🔹 HTTP — the protocol

### Methods (verbs)

| Method | Safe? | Idempotent? | Body? | Spring |
| --- | --- | --- | --- | --- |
| GET | ✅ | ✅ | ❌ | `@GetMapping` |
| HEAD | ✅ | ✅ | ❌ | rare |
| OPTIONS | ✅ | ✅ | ❌ | CORS preflight |
| POST | ❌ | ❌ | ✅ | `@PostMapping` (create/run) |
| PUT | ❌ | ✅ | ✅ | `@PutMapping` (replace) |
| PATCH | ❌ | ❌ | ✅ | `@PatchMapping` (partial update) |
| DELETE | ❌ | ✅ | optional | `@DeleteMapping` |

- **Safe** = no server side-effect
- **Idempotent** = N calls ≡ 1 call (client can retry without harm)
- **The bug:** `GET /orders/123/cancel` — pre-fetchers can fire it speculatively. Mutating ops MUST be POST/PUT/PATCH/DELETE.

### Status code families

| Family | Meaning | Hot examples |
| --- | --- | --- |
| 1xx | Informational | `100 Continue` |
| 2xx | Success | `200 OK`, `201 Created`, `204 No Content`, `202 Accepted` |
| 3xx | Redirect | `301`, `302`, `304 Not Modified` |
| 4xx | Client error | `400`, `401`, `403`, `404`, `409`, `429` |
| 5xx | Server error | `500`, `502`, `503`, `504` |

- **Retry rule:** retry `5xx` + `429`. Don't retry `4xx`.
- **Anti-pattern:** returning `200 OK` with `{"error":...}` body — use the right status code.

### Headers worth memorizing

| Header | What it does |
| --- | --- |
| `Host` | Virtual host disambiguation. **Mandatory in HTTP/1.1.** |
| `Content-Type` | Body MIME type. Spring uses this to pick `HttpMessageConverter`. |
| `Content-Length` | Body size. Parser uses it to know when body ends. |
| `Transfer-Encoding: chunked` | Streamed body (length unknown upfront). |
| `Accept` | What client wants back (`application/json` → Spring returns JSON). |
| `Authorization` | `Bearer <jwt>` / `Basic ...`. Spring Security filter reads this. |
| `Cookie` / `Set-Cookie` | Session state. |
| `Connection: keep-alive` | Reuse TCP socket for next request (HTTP/1.1 default). |
| `X-Forwarded-For` | Real client IP (set by LB). Behind LB, `request.getRemoteAddr()` returns LB's IP — use this header instead. |

---

## 🔹 TCP — the transport

### Port ranges

| Range | Name | Use |
| --- | --- | --- |
| 0–1023 | Well-known | `80` (HTTP), `443` (HTTPS), `22` (SSH), `5432` (Postgres). Root-only. |
| 1024–49151 | Registered | `8080`, `8443`, `9092` (Kafka), `6379` (Redis) |
| 49152–65535 | Ephemeral | OS-assigned for **client-side** outbound connections |

### A connection = a 4-tuple

```
(client IP, client port, server IP, server port)
```

Server port + IP are fixed; client port varies → that's how Tomcat on `:8080` handles thousands of concurrent connections.

### A socket = a file descriptor

- Unix: everything is a file. `lsof -i :8080` shows which process holds the port.
- `ulimit -n` = max file descriptors per process. Exhaust this → `Too many open files`.

### `TIME_WAIT` — the production gotcha

- After `close()`, the closing side holds the socket ~60s in `TIME_WAIT` (catches stray duplicate packets).
- **Client-side bug:** many short-lived outbound calls → ephemeral port exhaustion → `Cannot assign requested address`.
- **Fix:** use a connection pool with keep-alive on `RestTemplate` / Apache HttpClient.

### "Address already in use"

| Cause | Spot it | Fix |
| --- | --- | --- |
| Previous run alive | `lsof -ti :8080` | `kill <PID>` |
| `TIME_WAIT` lingering | `netstat -an \| grep 8080` | Wait, or `SO_REUSEADDR` (Tomcat does this by default) |

---

## 🔹 Servlet container

| Container | Notes |
| --- | --- |
| **Tomcat** | Default in Spring Boot; what `mcse_lite` uses |
| **Jetty** | Lighter; sometimes for tests/microservices |
| **Undertow** | Modern, async-first |

### WAR vs embedded — two deployment eras

| Era | Model | Example |
| --- | --- | --- |
| **Classical (2000s)** | Build `.war` → drop into pre-installed Tomcat → restart container | eg, in your app: `mcse_lite`-style legacy modules |
| **Boot (2015+)** | Build fat JAR with **embedded Tomcat** → `java -jar app.jar` | Modern services |

> "Spring Boot is a framework, not a server. It **embeds** a servlet container as a library."

---

## 🔹 Servlet API

```java
public interface Servlet {
    void init(ServletConfig config);
    void service(ServletRequest req, ServletResponse resp);
    void destroy();
}
```

- Nobody implements `Servlet` directly. Extend `HttpServlet`, override `doGet`/`doPost`.
- Container calls `init()` once, `service()` per request, `destroy()` on shutdown.

### The singleton rule (the bug everyone hits)

> **Servlets are SHARED instances. `@RestController` beans are singleton-scoped by default. Tomcat's worker pool calls methods on the SAME instance concurrently. NO mutable instance fields.**

```java
// ❌ race condition under load
private int counter = 0;

// ✅ thread-safe
private final AtomicInteger counter = new AtomicInteger();
```

---

## 🔹 Thread model

- Tomcat defaults: `maxThreads=200`, `acceptCount=100`, `maxConnections=8192`.
- Worker thread is **blocked for the entire `@Controller` method**.
- **Throughput math:** `threads × (1000 / latency_ms) = req/s`
  - 200 threads × (1000 / 5ms) = **40k req/s**
  - 200 threads × (1000 / 200ms) = **1k req/s** (40× worse!)
- **OS truth:** "blocked" thread = state `WAITING`, parked by kernel, uses 0 CPU but ~1 MB stack.
- **Symptom:** high latency + low CPU = threads parked on I/O.

### Async escape hatch (Servlet 3.0+)

- `request.startAsync()` releases the worker thread; a different thread completes the response later.
- Spring exposes via: returning `CompletableFuture<T>` / `DeferredResult<T>` / `Callable<T>` from a controller method.

---

## 🔹 Filter chain (the missing layer)

```
Request
  ↓
[ Filter 1: CharacterEncoding ]    ← pre-logic
[ Filter 2: SecurityFilterChain ]  ← Spring Security lives here
[ Filter 3: RequestLogging ]
  ↓
DispatcherServlet → @Controller method
  ↓
[ Filter 3: post-logic ]           ← runs on the way back out
[ Filter 2: post-logic ]
[ Filter 1: post-logic ]
  ↓
Response

Onion pattern: pre-logic outermost → inwards. Post-logic innermost → outwards.
```

### Filter vs HandlerInterceptor vs AOP — the discriminator

| Mechanism | Where it runs | Scope | Use for |
| --- | --- | --- | --- |
| **Servlet Filter** | Container level, BEFORE DispatcherServlet | All HTTP requests | Security, CORS, encoding, top-level logging |
| **HandlerInterceptor** | Inside DispatcherServlet, BEFORE the `@Controller` method | Spring MVC handlers only | Per-controller logging, locale, theme |
| **AOP `@Aspect`** | Around any Spring bean method | Any Spring bean | `@Transactional`, custom cross-cutting |

### Registering a filter (Spring Boot)

```java
@Bean
public FilterRegistrationBean<RequestLoggingFilter> loggingFilter() {
    FilterRegistrationBean<RequestLoggingFilter> bean = new FilterRegistrationBean<>();
    bean.setFilter(new RequestLoggingFilter());
    bean.addUrlPatterns("/*");
    bean.setOrder(1);   // lower = outer in the onion
    return bean;
}
```

---

## 🔹 `HttpServletRequest` → Spring annotation map

| Raw HTTP piece | Servlet API method | Spring annotation |
| --- | --- | --- |
| `POST` (method) | `getMethod()` | inferred from `@PostMapping` |
| `/orders/123` (URI) | `getRequestURI()` | `@PathVariable` extracts segments |
| `?source=web` | `getParameter("source")` | `@RequestParam("source")` |
| `Host: ...` (header) | `getHeader("Host")` | `@RequestHeader("Host")` |
| `Authorization: Bearer ...` | `getHeader("Authorization")` | `@RequestHeader` or Spring Security |
| `Cookie: sessionId=...` | `getCookies()` | `@CookieValue("sessionId")` |
| `{"item":"book"}` (body) | `getInputStream()` | `@RequestBody Pojo` (Jackson) |

**Rule of thumb:** every `@`-annotated parameter = one `HttpServletRequest` getter call + type conversion.

### Three scopes

| Scope | API | Lifetime |
| --- | --- | --- |
| Request | `req.setAttribute(...)` | One request |
| Session | `req.getSession().setAttribute(...)` | Across requests from same user (cookie-based) |
| Application | `req.getServletContext().setAttribute(...)` | App startup → shutdown |

---

## 🔹 Common bugs — one-liner each

| Bug | Cause | Fix |
| --- | --- | --- |
| Race on shared counter in `@RestController` | Singleton bean + mutable field | Local var or `AtomicInteger` |
| `GET /cancel` accidentally fires twice | Pre-fetchers hit safe GETs | Use POST/PUT/DELETE for mutations |
| `200 OK` masks failure | Wrong status code in body | `ResponseEntity.status(404).body(...)` |
| `@RequestBody` is null | `Content-Type` mismatch or wrong `Content-Length` | Send `Content-Type: application/json` |
| `getRemoteAddr()` returns LB's IP | Behind a load balancer | Read `X-Forwarded-For` instead |
| Outbound calls fail intermittently in load test | `TIME_WAIT` ephemeral port exhaustion | Keep-alive + connection pool |
| Service has high latency + low CPU | Workers parked on slow I/O | Tune pool / async / fix downstream |
| Security check inconsistent | Filter order wrong | Set `order` on `FilterRegistrationBean` |

---

## 🔹 Debugging instincts — symptom → cause

| Symptom | First suspect |
| --- | --- |
| High latency, low CPU | Blocked worker threads (slow DB / downstream) |
| `Connection refused` calling downstream | `TIME_WAIT` exhaustion / pool too small |
| `@RequestBody` is null | Wrong `Content-Type` or `Content-Length` |
| `Address already in use` on startup | Previous process / `TIME_WAIT` |
| Spring Security blocks/allows wrong endpoint | Filter ordering |
| Random data leaks across requests | Mutable instance field in controller |
| `Too many open files` | FD leak — unclosed sockets / DB connections |

---

## 🎤 Interview one-liners — rattle these off

| Question | One-sentence answer |
| --- | --- |
| **Web server vs app server vs servlet container?** | Web server originally served static files (nginx); servlet container runs Java servlets (Tomcat); app server is the full Java EE stack (WebSphere) — in modern usage Tomcat is colloquially called a web server. |
| **Walk through curl → my `@GetMapping`** | curl opens TCP, sends HTTP text; Tomcat acceptor thread grabs the socket, hands to a worker; worker parses HTTP into `HttpServletRequest`, runs the filter chain, then `DispatcherServlet` finds my `@GetMapping` via `HandlerMapping`, invokes it via `HandlerAdapter`; my method returns; Jackson serializes; bytes flow back; TCP keep-alive holds the connection. |
| **Why is a `@RestController` instance field a bug?** | Spring beans are singletons by default; Tomcat's 200-thread pool calls methods on the same instance concurrently — instance fields = shared mutable state = race condition. |
| **Is Spring Boot a web server?** | No, it's a framework that **embeds** a servlet container (Tomcat by default) — the embedded Tomcat is what listens on the port. |
| **Full lifecycle of an HTTPS request to a Spring Boot service?** | DNS → TCP 3-way handshake → TLS (usually terminated at LB) → HTTP bytes → Tomcat acceptor → worker thread parses to `HttpServletRequest` → filter chain → `DispatcherServlet` → my `@Controller` (blocks on I/O) → `HttpMessageConverter` serializes → filters' post-logic → response bytes → keep-alive holds socket. |
| **Filter vs HandlerInterceptor?** | Filter runs at container level on ALL HTTP requests (good for security, CORS, encoding); HandlerInterceptor runs inside DispatcherServlet only for Spring MVC handlers (good for per-controller logic that needs to know which method ran). |
| **High latency, low CPU — what's wrong?** | Workers parked on I/O waits; Tomcat threads blocked waiting on DB/downstream; with 200 threads and 200ms per request, peak is 1k req/s regardless of CPU. Fix: connection pool, async servlets, or reactive. |
| **What does `@RequestParam("x")` actually do?** | Spring calls `request.getParameter("x")` and binds the return value to my method parameter with type conversion — same pattern for `@PathVariable` (URI template), `@RequestHeader` (header), `@CookieValue` (cookie), `@RequestBody` (input stream + Jackson). |
| **How does Spring Security work, briefly?** | It's a `SecurityFilterChain` registered in the servlet filter chain — every request passes through filters that handle authentication, CSRF, CORS, authorization, before reaching the controller. |

---

## 🧾 The 60-second mental rehearsal (recite the morning of the interview)

> *"When a curl hits my Spring Boot service:*
>
> *DNS resolves the hostname. TCP does the 3-way handshake. If HTTPS, TLS handshakes — usually terminated at the load balancer, so Tomcat sees plain HTTP.*
>
> *The HTTP request — plain text — arrives at Tomcat's listening socket. The acceptor thread accepts the connection and hands it to a worker thread from a pool of 200. The worker reads the bytes, parses them into a `HttpServletRequest` object.*
>
> *The request flows through the filter chain — character encoding, Spring Security, custom logging — each filter wraps the next like an onion.*
>
> *After filters, `DispatcherServlet` — the one servlet Spring registers — takes over. `HandlerMapping` matches the URI to my `@GetMapping` method. `HandlerAdapter` invokes it, resolving `@PathVariable`, `@RequestParam`, `@RequestBody` from the request.*
>
> *My method runs. The worker thread is blocked for the whole duration — that's why slow DB calls hang the service. I return an object; Jackson serializes it to JSON via `HttpMessageConverter`; the response bytes flow back through the filters' post-logic and out the socket. HTTP/1.1 keep-alive holds the TCP connection open for the next request.*
>
> *Every `@`-annotation on a controller method is sugar over a `HttpServletRequest` getter call. Every layer above my method is the container's job. That's the four layers, six stages, and one filter chain that turn raw bytes into my business logic."*

---

## 📚 Companion links

- **Full study notes:** `../DeepDive/01-web-fundamentals.md`
- **Master plan:** `../spring-10-hour-plan.md` (Day 1 entry)
- **Track status:** `../spring-prep-log.md`
- **Universal style rules:** `../../../AGENTS.md`
- **JavaBackend subdomain rules:** `../../AGENTS.md`

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| 2026-05-18 | Created as the post-DeepDive revision aid. Distilled from the ~1380-line DeepDive into ~350 lines of tables + bullets + one-liners. Includes the 60-second mental rehearsal for interview-morning use. |
