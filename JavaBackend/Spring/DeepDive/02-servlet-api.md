# Day 2 — The Servlet API

> **Track:** Spring Foundation 10-Hour. **Companion:** `../Reference/02-servlet-api-reference.md` (compact cheatsheet, read the night before the interview). **Plan entry:** `../spring-10-hour-plan.md` § Day 2.

---

## 📖 Prerequisite

You should have absorbed Day 1 (`01-web-fundamentals.md`):
- A TCP socket is just a file descriptor; HTTP is text on top of TCP.
- A servlet container (Tomcat / Jetty) parses HTTP and routes the parsed request into your code via the Servlet contract.
- Tomcat's default model: **one servlet instance**, **many request threads** drawn from a pool (`maxThreads=200`).

If any of that is fuzzy, re-read Day 1 Part 1 (Layer 3) and Part 2 (the request pipeline). This day picks up where the container hands control to *your* code.

---

## 🧠 Mental model

> **A servlet is a contract: "give me a `(request, response)` pair and I'll do something."** The container's job is to call your `service()` method with that pair, on a thread from its pool, exactly once per HTTP request. `HttpServlet` is a convenience class that dispatches `service()` to the conventional verb-named methods (`doGet`, `doPost`, ...). **Everything else in Spring MVC — `DispatcherServlet`, `@Controller`, `@RestController` — is sugar built on top of this one method.**

Three corollaries follow directly from the contract:

1. **Servlets are singletons by default.** The container creates ONE instance and shares it across all request threads. **Instance fields are shared state** — touching them without synchronization is a bug.
2. **The lifecycle is fixed.** `init()` runs once before the first request; `service()` runs N times concurrently; `destroy()` runs once at shutdown. That's the whole API surface.
3. **`HttpServletRequest` and `HttpServletResponse` are the raw API.** Every Spring annotation you've ever used (`@PathVariable`, `@RequestParam`, `@RequestBody`, `@ResponseBody`, `@RequestHeader`, ...) is just a typed wrapper around a getter on these two objects.

If you can verbalize those three points without notes, you have Day 2.

---

## 🪜 Concept build-up

Going from the bare contract → lifecycle → request/response API → registration → embedded vs standalone, in that order. Each layer assumes the previous one.

---

### Part 1 — The `Servlet` contract (the 5-method interface)

The Servlet specification (now `jakarta.servlet.Servlet`; pre-Jakarta-rename it was `javax.servlet.Servlet`) defines ONE interface with **five** methods:

```java
// jakarta.servlet.Servlet
public interface Servlet {
    void init(ServletConfig config) throws ServletException;
    void service(ServletRequest req, ServletResponse res) throws ServletException, IOException;
    void destroy();
    ServletConfig getServletConfig();
    String getServletInfo();
}
```

The two that matter every day: `init` and `service`. The other three you almost never touch directly.

#### What the container actually does with this interface

Container pseudocode, simplified:

```java
// Step 1 — bootstrap: instantiate your servlet (once)
Servlet myServlet = (Servlet) Class.forName(servletClassName).getDeclaredConstructor().newInstance();

// Step 2 — initialize (once, before any request)
myServlet.init(servletConfigObject);

// Step 3 — for every incoming HTTP request, on a pool thread, call service()
threadPool.execute(() -> {
    HttpServletRequest req = parseRequest(socket);
    HttpServletResponse res = buildResponseObject(socket);
    myServlet.service(req, res);
});

// Step 4 — on shutdown
myServlet.destroy();
```

**The takeaway:** the container owns the lifecycle and the threading. You own what happens *inside* `service()`. The container will never call `init` again after the first time, will never re-instantiate your servlet between requests, and will absolutely call `service()` from multiple threads in parallel.

> **Lesson learned the hard way (May 2026):** Forgetting that "the container owns instantiation" makes you write code like `private final List<String> recentRequests = new ArrayList<>()` inside a servlet — and then wonder why production sees `ArrayIndexOutOfBoundsException` under load. The list is shared; ArrayList isn't thread-safe; concurrent `add()` corrupts internal state. Fix: don't put per-request state in instance fields. Use local variables inside `service()` or a `ConcurrentHashMap` / atomic if you genuinely need shared cross-request state.

---

### Part 2 — `HttpServlet`: the convenience class

The raw `Servlet` interface is protocol-agnostic. To make HTTP ergonomic, the spec ships `HttpServlet` — an abstract class that **implements `service()` and dispatches to method-specific handlers**.

You almost never extend `Servlet` directly. You extend `HttpServlet`.

#### What `HttpServlet.service()` does (simplified source)

```java
// jakarta.servlet.http.HttpServlet
public abstract class HttpServlet extends GenericServlet {

    // Container calls THIS service(req, res). It downcasts and dispatches.
    @Override
    public void service(ServletRequest req, ServletResponse res)
            throws ServletException, IOException {
        // Step 1 — sanity-cast from the generic ServletRequest to HttpServletRequest
        HttpServletRequest httpReq = (HttpServletRequest) req;
        HttpServletResponse httpRes = (HttpServletResponse) res;
        // Step 2 — delegate to the HTTP-aware overload
        service(httpReq, httpRes);
    }

    // The verb-dispatching service() — this is what we usually mean by "service()"
    protected void service(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        String method = req.getMethod();
        if (method.equals("GET")) {
            doGet(req, res);
        } else if (method.equals("POST")) {
            doPost(req, res);
        } else if (method.equals("PUT")) {
            doPut(req, res);
        } else if (method.equals("DELETE")) {
            doDelete(req, res);
        } else if (method.equals("HEAD")) {
            doHead(req, res);
        } else if (method.equals("OPTIONS")) {
            doOptions(req, res);
        } else if (method.equals("TRACE")) {
            doTrace(req, res);
        } else {
            res.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }
    }

    // Default doGet — returns 405 unless you override
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        res.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    // (doPost / doPut / doDelete / etc. all default to 405 the same way)
}
```

**The key insight:** by overriding `doGet` / `doPost` etc., you're plugging into a `switch (method)` that `HttpServlet` is running for you. You can also override `service(HttpServletRequest, HttpServletResponse)` directly if you want to bypass the dispatch — Spring's `DispatcherServlet` does exactly this (more on that on Day 6).

---

### Part 3 — The lifecycle in detail

The full timeline of one servlet, from container start to container shutdown:

```
container starts
      │
      ▼
┌──────────────────────────┐
│ 1. new MyServlet()        │  ← reflective no-arg ctor; NO dependencies wired
└──────────────────────────┘
      │
      ▼
┌──────────────────────────┐
│ 2. init(servletConfig)    │  ← container calls this ONCE, before any request
│    - read init params     │
│    - open resources       │
│    - cache reference data │
└──────────────────────────┘
      │
      ▼
┌──────────────────────────────────────────────────────────────┐
│ 3. service(req, res)  service(req, res)  service(req, res)   │
│       (thread-1)        (thread-2)         (thread-3)        │
│                                                              │
│    Called N times, concurrently, from request worker threads │
│    Servlet instance is SHARED — your code must be thread-safe │
└──────────────────────────────────────────────────────────────┘
      │
      ▼
┌──────────────────────────┐
│ 4. destroy()              │  ← container calls this ONCE at shutdown
│    - close DB connections │
│    - flush caches         │
│    - release resources    │
└──────────────────────────┘
      │
      ▼
container shuts down, servlet instance is GC'd

KEY INVARIANT:
   init() runs serial-before-everything.
   destroy() runs serial-after-everything.
   service() runs N times concurrently in between.
   You get exactly ONE instance for the lifetime.
```

#### `init(ServletConfig)` — the one-shot setup

`ServletConfig` gives you:
- `getInitParameter(name)` — per-servlet init params (declared in `web.xml` or `@WebServlet`)
- `getServletContext()` — application-wide context (shared across all servlets in the same web app)
- `getServletName()` — the registered name (useful for logging)

Two flavors of `init`:

```java
public class MyServlet extends HttpServlet {

    // Flavor 1 — override the no-arg version (recommended)
    @Override
    public void init() throws ServletException {
        // ServletConfig already cached by GenericServlet — just use it
        String mode = getInitParameter("mode");
        // open resources, cache references, etc.
    }

    // Flavor 2 — override the ServletConfig version (rarely needed)
    @Override
    public void init(ServletConfig config) throws ServletException {
        // CRITICAL: call super.init(config) first — it stores the config so
        // getInitParameter / getServletContext work afterward
        super.init(config);
        String mode = config.getInitParameter("mode");
    }
}
```

❌ **Pitfall:** if you override the `ServletConfig` version and forget `super.init(config)`, every subsequent `getInitParameter(...)` call returns `null` because the parent class's stored `ServletConfig` field is `null`. This is the most common `init()` bug.

#### `service()` — the hot path

This is where every request lands. **It runs concurrently.** Treat each `service()` invocation as if it might be one of 200 parallel calls.

Three rules for `service()` code:
1. **No mutable instance fields without synchronization.** Local variables are fine — they live on the request thread's stack.
2. **Don't hold locks across I/O.** If you `synchronized(this)` and then call a slow DB, you've serialized all 200 threads onto one. Defeats the whole point of the pool.
3. **Always finish the response.** `resp.getWriter().flush()` (or close it via try-with-resources) — some containers send empty responses if you don't.

#### `destroy()` — the polite shutdown

Called by the container when:
- The web app is being undeployed (e.g., redeployed in dev)
- The container is shutting down (SIGTERM)

It is **NOT** called if the container crashes (`kill -9`, OOM, JVM segfault). So treat anything in `destroy()` as best-effort, not as a guarantee. **Database connections, file handles, network sockets** that MUST be released should also be backed by a finalizer/shutdown-hook at the resource layer — never count on `destroy()` alone.

```java
@Override
public void destroy() {
    try {
        if (dbConnectionPool != null) {
            dbConnectionPool.close();
        }
    } catch (Exception e) {
        // log but don't throw — container is shutting down anyway
        log.error("error closing pool on servlet destroy", e);
    }
    super.destroy();
}
```

---

### Part 4 — `HttpServletRequest` and `HttpServletResponse`: the raw API

Every Spring annotation you've ever used wraps a getter on one of these two objects. Internalize the raw API and Spring becomes "oh, that's just `req.getX()`."

#### `HttpServletRequest` — the inputs

Grouped by what they expose:

| Category | Method | Returns | Spring equivalent |
| --- | --- | --- | --- |
| URL parts | `getRequestURI()` | `/orders/123` (path only, no query) | (auto from `@PathVariable`-bearing mapping) |
| URL parts | `getRequestURL()` | `http://host/orders/123` (full URL) | rarely needed |
| URL parts | `getQueryString()` | `id=42&sort=asc` (raw, after `?`) | (auto from `@RequestParam`) |
| URL parts | `getPathInfo()` | path after servlet mapping | rarely needed in Spring |
| Method | `getMethod()` | `GET`, `POST`, ... | `@GetMapping` / `@PostMapping` / ... |
| Headers | `getHeader(name)` | single header value | `@RequestHeader("name")` |
| Headers | `getHeaders(name)` | all values for a repeated header | `@RequestHeader List<String>` |
| Headers | `getHeaderNames()` | enumeration of header names | `@RequestHeader Map<String,String>` |
| Params | `getParameter(name)` | first value of `name` from query OR form body | `@RequestParam("name")` |
| Params | `getParameterValues(name)` | all values (for `?tag=a&tag=b`) | `@RequestParam List<String>` |
| Params | `getParameterMap()` | `Map<String, String[]>` | `@RequestParam Map<String,String>` |
| Body | `getReader()` / `getInputStream()` | raw body stream | `@RequestBody MyDto` (after deserialization) |
| Cookies | `getCookies()` | `Cookie[]` | `@CookieValue("name")` |
| Session | `getSession()` / `getSession(false)` | `HttpSession` | `@SessionAttribute` |
| Attributes | `getAttribute` / `setAttribute` | per-request scratch map | `HttpServletRequest` directly, or `WebRequest` |
| Identity | `getRemoteAddr()` | client IP (or proxy IP) | rare |

The Spring → raw API mapping is **almost mechanical**. `@RequestParam("id")` = `req.getParameter("id")` + type conversion + null-handling. `@RequestHeader("X-Trace-Id")` = `req.getHeader("X-Trace-Id")`. `@RequestBody` = `req.getReader()` piped through a Jackson `ObjectMapper`. Knowing this is what lets you reason about Spring instead of memorizing it.

#### `HttpServletResponse` — the outputs

| Category | Method | What it does | Spring equivalent |
| --- | --- | --- | --- |
| Status | `setStatus(200)` | sets numeric status code | `ResponseEntity.ok()` etc. |
| Status | `sendError(404, "msg")` | short-circuits + error page | throw exception → `@ExceptionHandler` |
| Headers | `setHeader(name, value)` | sets / replaces a response header | `ResponseEntity.header(...)` |
| Headers | `addHeader(name, value)` | adds another value (for repeatable headers) | (same) |
| Headers | `setContentType("application/json")` | sets `Content-Type` | (auto from `@RequestMapping(produces=...)`) |
| Body | `getWriter()` | `PrintWriter` for text/JSON | (auto from `@ResponseBody` + Jackson) |
| Body | `getOutputStream()` | `ServletOutputStream` for binary | rare in Spring |
| Cookies | `addCookie(cookie)` | adds a `Set-Cookie` header | (manual `HttpServletResponse` injection) |
| Redirect | `sendRedirect(url)` | 302 to another URL | `return "redirect:/url"` from controller |

> **Critical rule about ordering:** you MUST set status, headers, and content type **before** writing any body bytes. Once a single byte is written to the output stream, the response is **committed** — headers are flushed to the wire and changing them is no longer possible. Calling `setHeader` after that point is silently ignored on most containers.

---

### Part 5 — Registering a servlet (three ways across history)

How does the container know which class to instantiate for which URL? Three answers, listed from oldest to newest:

#### 5a. `web.xml` (Servlet 2.x and earlier — pre-2009)

Declarative XML in `src/main/webapp/WEB-INF/web.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
         version="6.0">

    <servlet>
        <servlet-name>helloServlet</servlet-name>
        <servlet-class>com.kapil.demo.HelloServlet</servlet-class>
        <init-param>
            <param-name>mode</param-name>
            <param-value>greeting</param-value>
        </init-param>
        <load-on-startup>1</load-on-startup>
    </servlet>

    <servlet-mapping>
        <servlet-name>helloServlet</servlet-name>
        <url-pattern>/hello</url-pattern>
    </servlet-mapping>

</web-app>
```

Three things to note:
- **`<servlet>` block** declares the class and any init params.
- **`<servlet-mapping>` block** wires the servlet name to a URL pattern.
- **`<load-on-startup>1</load-on-startup>`** = "call `init()` at container boot, not lazily on first request." Lower numbers load first. Use this for servlets whose `init` is slow (cache pre-population, etc.).

URL patterns can be:
- **Exact match:** `/hello` — matches only `/hello`
- **Path prefix:** `/api/*` — matches anything under `/api`
- **Extension:** `*.jsp` — matches by file extension
- **Default:** `/` — catch-all when no other pattern matches

This is what `DispatcherServlet` historically got mapped to: `<url-pattern>/</url-pattern>` so it catches everything.

#### 5b. `@WebServlet` annotation (Servlet 3.0+ — 2009 onward)

Same registration, in Java:

```java
// jakarta.servlet.annotation.WebServlet
@WebServlet(
    name = "helloServlet",
    urlPatterns = "/hello",
    loadOnStartup = 1,
    initParams = {
        @WebInitParam(name = "mode", value = "greeting")
    }
)
public class HelloServlet extends HttpServlet {
    // ...
}
```

The container does classpath scanning at boot, finds `@WebServlet`-annotated classes, and registers them. **No `web.xml` needed.**

#### 5c. Programmatic registration via `ServletContainerInitializer` (Servlet 3.0+)

The most flexible. A piece of code that runs at container boot and registers servlets dynamically:

```java
public class MyInitializer implements ServletContainerInitializer {
    @Override
    public void onStartup(Set<Class<?>> handledTypes, ServletContext ctx) {
        // Register a servlet at runtime
        ServletRegistration.Dynamic reg = ctx.addServlet("helloServlet", new HelloServlet());
        reg.addMapping("/hello");
        reg.setLoadOnStartup(1);
        reg.setInitParameter("mode", "greeting");
    }
}
```

This is registered via the SPI mechanism — drop a file at `META-INF/services/jakarta.servlet.ServletContainerInitializer` containing the FQN of your initializer class, and the container will find and call it.

**This is how Spring registers `DispatcherServlet`.** Spring ships `SpringServletContainerInitializer` (implements `ServletContainerInitializer`). It finds your `WebApplicationInitializer` classes, instantiates them, and calls their `onStartup` — and that's where `DispatcherServlet` gets added to the `ServletContext`. **No `web.xml` required for modern Spring apps** — the registration happens programmatically at boot.

> **Connection back to Day 6:** when we look at `DispatcherServlet` registration, this is the mechanism. Spring Boot collapses it even further — Boot's auto-configuration just declares a `DispatcherServlet` `@Bean` and the embedded Tomcat / Jetty maps it to `/`.

---

### Part 6 — Embedded vs standalone containers

Two deployment models. **The model has changed since ~2014.**

#### 6a. Old model — standalone container, WAR deployment

You build a `.war` file. You install Tomcat / Jetty / WebLogic / JBoss as a long-running service on a server. You drop your WAR into `webapps/`. The container unpacks it, reads `web.xml` (and/or scans for `@WebServlet`), starts your servlets.

**Strengths:** one container hosts many apps (resource sharing). **Weaknesses:** the container is operations' problem, not the dev's. Container version mismatches between dev laptop and prod is a constant source of "works on my machine" bugs.

#### 6b. New model — embedded container, executable JAR

You include Tomcat (or Jetty / Undertow) as a Maven dependency in your app. You start the container *from your own `main()` method*. You ship one fat JAR. `java -jar myapp.jar` is the deployment step.

```java
public class Main {
    public static void main(String[] args) throws Exception {
        // Step 1 — create an embedded Jetty server bound to port 8080
        Server server = new Server(8080);

        // Step 2 — create a servlet context (the web-app boundary)
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        server.setHandler(context);

        // Step 3 — register a servlet at a URL pattern (programmatic, no web.xml)
        context.addServlet(new ServletHolder(new HelloServlet()), "/hello");

        // Step 4 — start the server; this blocks until shutdown signal
        server.start();
        server.join();
    }
}
```

**This is what Spring Boot does at runtime.** The Boot starter pulls in `spring-boot-starter-tomcat` (or `-jetty`), and Boot's auto-config wires up an embedded Tomcat almost exactly like the snippet above, then registers `DispatcherServlet` into it.

**Why embedded won:**
- **Dev parity:** the dev laptop runs the same Tomcat version as prod, because it's bundled in the JAR.
- **Single artifact:** the JAR is the deliverable — no separate container install step.
- **Cloud-native fit:** containers / Kubernetes pods want one process; `java -jar app.jar` is one process. WAR-in-Tomcat assumes a long-lived container hosting many apps — orthogonal to the pod model.

> **What "embedded" really means:** Tomcat as a library, not a runtime. You construct it, configure it, start it, all from your code. The roles flip — your code drives the container instead of the container hosting your code.

---

## 🎨 Visual — the full Day-2 picture

```
                         ┌────────────────────────────────────────┐
                         │           Container (Tomcat/Jetty)      │
                         │  ┌─────────────────────────────────┐    │
                         │  │  Worker thread pool (200 threads) │   │
HTTP request ───────────►│  └─────────────────────────────────┘   │
(socket accepted)        │              │                          │
                         │              ▼                          │
                         │  ┌─────────────────────────────────┐    │
                         │  │  HTTP parser → HttpServletRequest │   │
                         │  │                  HttpServletResponse  │
                         │  └─────────────────────────────────┘    │
                         │              │                          │
                         │              ▼                          │
                         │  ┌─────────────────────────────────┐    │
                         │  │  ServletContext.servletRegistry  │    │
                         │  │  match URL → servlet instance    │    │
                         │  └─────────────────────────────────┘    │
                         │              │                          │
                         │              ▼                          │
                         │  ┌─────────────────────────────────┐    │
                         │  │ servlet.service(req, res)        │    │
                         │  │   ├─ if method == "GET"  ──► doGet │   │
                         │  │   ├─ if method == "POST" ──► doPost│   │
                         │  │   └─ ...                          │   │
                         │  └─────────────────────────────────┘    │
                         │              │                          │
                         │              ▼                          │
                         │  ┌─────────────────────────────────┐    │
                         │  │  resp.getWriter().write(...)     │    │
                         │  │  resp.flushBuffer()              │    │
                         │  └─────────────────────────────────┘    │
                         │              │                          │
                         └──────────────┼──────────────────────────┘
                                        ▼
                              HTTP response on socket

KEY INVARIANT:
   ONE servlet instance, MANY threads calling service() concurrently.
   The container owns the threading; your servlet code must be thread-safe.
   Everything written to resp BEFORE flushBuffer() can be changed; AFTER, the
   response is committed and changes are silently ignored.
```

---

## ⚠️ Common mistakes (the silent-bug hall of fame)

### Bug 1 — instance fields used for per-request state

```java
public class CounterServlet extends HttpServlet {

    // ❌ WRONG — shared across all 200 threads
    private int requestCount = 0;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws IOException {
        requestCount++;   // not atomic, not volatile — DOOM
        res.getWriter().write("Request #" + requestCount);
    }
}
```

**What happens:** under load, `requestCount++` is read-modify-write. Two threads can read the same value before either writes back, so the counter is silently undercounted. Worse, with `ArrayList` instance fields, you get `ConcurrentModificationException` or corrupted internal state.

**Fix:**
```java
// ✅ Use an atomic type (Java's built-in thread-safe counter)
private final AtomicInteger requestCount = new AtomicInteger(0);

@Override
protected void doGet(HttpServletRequest req, HttpServletResponse res)
        throws IOException {
    int n = requestCount.incrementAndGet();
    res.getWriter().write("Request #" + n);
}
```

**Better fix:** don't track per-request state in the servlet at all. Push it to a dedicated metric/counter library (`Micrometer`, etc.).

---

### Bug 2 — forgetting to flush / close the response writer

```java
@Override
protected void doGet(HttpServletRequest req, HttpServletResponse res)
        throws IOException {
    PrintWriter out = res.getWriter();
    out.write("hello");
    // ❌ No flush, no close. Some containers send empty body.
}
```

**What happens:** the container buffers the body. If your code returns without flushing AND the container's auto-flush-on-return logic disagrees (some do, some don't, some only do above a buffer threshold), the client gets a 200 response with an empty body. Maddening to debug because curl shows "no error."

**Fix — explicit flush:**
```java
@Override
protected void doGet(HttpServletRequest req, HttpServletResponse res)
        throws IOException {
    PrintWriter out = res.getWriter();
    out.write("hello");
    out.flush();
}
```

**Or — try-with-resources to auto-close (which flushes):**
```java
@Override
protected void doGet(HttpServletRequest req, HttpServletResponse res)
        throws IOException {
    try (PrintWriter out = res.getWriter()) {
        out.write("hello");
    }
    // close() implicitly calls flush()
}
```

---

### Bug 3 — setting headers after the body is written

```java
@Override
protected void doGet(HttpServletRequest req, HttpServletResponse res)
        throws IOException {
    res.getWriter().write("hello");
    // ❌ Too late — response is committed
    res.setHeader("X-Trace-Id", "abc-123");
    res.setStatus(201);
}
```

**What happens:** the moment you write the first byte, the container sends `HTTP/1.1 200 OK\r\nContent-Type: ...\r\n\r\n...` to the wire. Headers and status are gone. Subsequent `setHeader` / `setStatus` calls return without error but do nothing.

**Fix — set everything before writing:**
```java
@Override
protected void doGet(HttpServletRequest req, HttpServletResponse res)
        throws IOException {
    res.setStatus(201);
    res.setHeader("X-Trace-Id", "abc-123");
    res.setContentType("application/json");
    // Now write the body
    res.getWriter().write("{\"ok\":true}");
}
```

> **Self-check:** `res.isCommitted()` returns `true` once the response has been flushed. If you're not sure whether you're past the point of no return, call it.

---

### Bug 4 — overriding `init(ServletConfig)` without `super.init(config)`

```java
public class BrokenServlet extends HttpServlet {

    @Override
    public void init(ServletConfig config) throws ServletException {
        // ❌ Forgot to call super.init(config)
        String mode = getInitParameter("mode");
        // mode is null — getInitParameter delegates to a field set by super.init()
    }
}
```

**Fix — either call super, or override the no-arg `init()` instead:**

```java
// Option A — call super
@Override
public void init(ServletConfig config) throws ServletException {
    super.init(config);
    String mode = getInitParameter("mode");   // now works
}

// Option B — override no-arg (recommended)
@Override
public void init() throws ServletException {
    String mode = getInitParameter("mode");   // works because super.init(config) already ran
}
```

---

### Bug 5 — opening a DB connection in `init()` and never closing it on `destroy()`

```java
public class LeakyServlet extends HttpServlet {

    private Connection dbConn;

    @Override
    public void init() throws ServletException {
        try {
            dbConn = DriverManager.getConnection(jdbcUrl);
        } catch (SQLException e) {
            throw new ServletException(e);
        }
    }

    // ❌ No destroy() — connection leaks on redeploy in dev
}
```

**Fix:**
```java
@Override
public void destroy() {
    try {
        if (dbConn != null && !dbConn.isClosed()) {
            dbConn.close();
        }
    } catch (SQLException e) {
        log.error("error closing db conn on servlet destroy", e);
    }
    super.destroy();
}
```

**Better fix:** don't manage connections from a servlet. Use a pool (`HikariCP`) wired as a singleton bean. Spring Boot does this for you. We'll see it on Day 9.

---

## 🏢 Where you've seen this in your app

Open any `@RestController` in your day-job codebase (e.g., `mcse_lite/.../HollowCacheServiceController.java`). Mentally translate:

| Annotation | Raw servlet equivalent |
| --- | --- |
| `@RestController` | `extends HttpServlet` + writes JSON in every `doX` |
| `@GetMapping("/cache/{id}")` | `doGet` + URL pattern matching on `getPathInfo()` |
| `@PathVariable("id") String id` | `String id = req.getPathInfo().split("/")[1]` |
| `@RequestParam("q") String q` | `String q = req.getParameter("q")` |
| `@RequestBody RefreshDto dto` | `mapper.readValue(req.getReader(), RefreshDto.class)` |
| `return cache.get(id)` (method returns object) | `res.getWriter().write(mapper.writeValueAsString(obj))` |

**The whole controller IS a servlet method.** Spring's `DispatcherServlet` does the routing and de/serialization, then calls into your `@Controller` method — which is conceptually still a `doGet` body.

> The DispatcherServlet itself is just a servlet that delegates each request to a `@Controller` method via `HandlerMapping` + `HandlerAdapter`. Day 6 covers that handoff in detail. For now: every `@RestController` you've ever written is sitting on top of one shared `DispatcherServlet` instance that's running in a `service()` loop.

---

## 🎤 Interview Q&A

### Q1. "Walk me through what happens between a `curl` request and your `doGet` being called."

**Model answer:**

> Curl opens a TCP connection to the server's port, sends the HTTP request bytes — `GET /orders/123 HTTP/1.1` plus headers and a blank line. The container's acceptor thread reads from the socket and pulls bytes off until it sees the end-of-headers marker. It parses those bytes into an `HttpServletRequest` object — populating method, URI, headers, params, etc. It then looks up which servlet is mapped to `/orders/123` via the servlet context's registry. Once it has the servlet instance, it grabs a thread from the worker pool and on that thread calls `servlet.service(req, res)`. `HttpServlet.service()` inspects the method, sees `GET`, and dispatches to `doGet(req, res)`. **My code runs.** Whatever I write to `res.getWriter()` is buffered; when `service()` returns (or I call `flushBuffer()`), the container serializes status line + headers + body and writes them to the socket. Connection stays open if keep-alive is on, otherwise closes.

### Q2. "Why are servlet instance fields dangerous?"

**Model answer:**

> Because the container creates exactly ONE instance of my servlet and reuses it across all request threads. Tomcat's default pool is 200 threads — so up to 200 threads can be inside the same `doGet` method on the same instance simultaneously. Any instance field they touch is shared mutable state. Unless that field is an `AtomicInteger` / `ConcurrentHashMap` / something explicitly thread-safe, I have a race condition. Local variables inside the method are fine — they live on the request thread's stack, one per call. The rule I follow: **instance fields are config / dependencies (set once in `init`, read-only forever after); local variables are per-request state.**

### Q3. "What's the difference between `init()` and the constructor?"

**Model answer:**

> The constructor runs when the container reflectively instantiates the servlet — which means there's no `ServletConfig` yet, no `ServletContext`, no init params, no logger from the container, nothing. It's basically useless for setup. `init()` runs **after** the container has wired up the config object, so you can read init params, access the `ServletContext`, and do real initialization (open connections, load caches, register listeners). **You override `init`, never the constructor.** Also, `init` is allowed to throw `ServletException` to signal "I can't start" — constructors can't communicate failure cleanly.

### Q4. "If you put `synchronized` on your `doGet`, what's the throughput impact?"

**Model answer:**

> Catastrophic. `synchronized` on an instance method synchronizes on the servlet instance — which is a singleton. So now all 200 worker threads serialize through one method. If `doGet` makes a 100ms DB call, you've gone from "200 concurrent requests at 100ms each = 200 req/sec capacity" to "1 request at a time at 100ms each = 10 req/sec capacity." Twenty-fold throughput drop. **The fix is to not need synchronization.** Make your shared state thread-safe (`AtomicX`, `ConcurrentMap`), or push state into per-request local variables, or scope to a downstream service that handles its own concurrency. The servlet itself should be stateless past `init()`.

### Q5. "What's `@WebServlet` and how does it relate to `web.xml`?"

**Model answer:**

> Both are servlet *registration* mechanisms. `web.xml` is the classic XML declaration — `<servlet>` block names the class, `<servlet-mapping>` wires it to a URL. `@WebServlet` (added in Servlet 3.0, 2009) is the annotation equivalent — drop `@WebServlet(urlPatterns = "/foo")` on your `HttpServlet` subclass and the container will pick it up via classpath scanning at boot. You can use either or both; they're additive. **In modern Spring Boot apps, neither is used directly** — Spring registers `DispatcherServlet` programmatically via `WebApplicationInitializer` (which the container picks up via `ServletContainerInitializer`'s SPI). But under the hood it's the same registration API.

---

## 🧾 TL;DR / mental hook

> **A servlet is one shared object that the container calls concurrently from many threads.** `init()` runs once, before all requests. `service()` runs N times in parallel, one per request. `destroy()` runs once at shutdown. `HttpServletRequest` and `HttpServletResponse` are the raw API; **every Spring annotation is a typed wrapper around their getters and setters.** Instance fields are config-only. Per-request state lives on the stack inside `service()`. **Get this model right and `DispatcherServlet` (Day 6) is just "a servlet that delegates to `@Controller` methods."**

---

## 📚 Companion links

- **Reference (cheatsheet for revising):** `../Reference/02-servlet-api-reference.md`
- **Plan entry:** `../spring-10-hour-plan.md` § Day 2
- **Prep log:** `../spring-prep-log.md`
- **Previous day (prerequisite):** `01-web-fundamentals.md`
- **Next day:** `03-ioc-di-container.md` (the IoC container — once you have a servlet, who *creates* it and *wires its dependencies*?)
- **Practice code:**
  - `../Practice/exercises/01-servlet-hello/` — minimal hello-world servlet on embedded Jetty
  - `../Practice/growing-app/v1-servlet/` — `GET /orders/{id}` v1 (compare with v2 Spring MVC on Day 6, v3 Spring Boot on Day 7)

---

### Changelog

| Date | Change |
| --- | --- |
| 2026-05-18 | Day 2 DRAFT written. Five parts (contract → HttpServlet → lifecycle → request/response API → registration → embedded). Five common-bugs callouts. Five-question Q&A appendix. Includes thread-model invariant from Day 1 carried forward. |
