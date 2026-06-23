# Exercise 01 — Hello-world servlet on embedded Jetty

> **Chapter 1.** Companion to `../../../DeepDive/01-web-servlet-foundation.md` (Part C). Smallest possible servlet that exercises the full lifecycle: `init` → `service` (via `doGet`) → `destroy`.

---

## 🎯 What this demonstrates

1. **The Servlet contract is tiny** — one class extending `HttpServlet` with `init`, `doGet`, and `destroy` overrides. That's it.
2. **`@WebServlet` registers the servlet** — no `web.xml` declaration needed (Servlet 3.0+).
3. **`init()` fires once at boot** — proven by `loadOnStartup=1` + the log line on startup.
4. **`doGet()` fires concurrently** — the request-thread name in the log varies under load.
5. **`AtomicInteger` for the shared counter** — the textbook example of "instance fields must be thread-safe."
6. **Headers / status before body** — the writer is closed via try-with-resources so flushing is guaranteed.

---

## 🚀 How to run

From this folder:

```bash
mvn jetty:run
```

First run downloads Jetty + servlet API (~30 seconds). Subsequent runs start in 1–2 seconds.

Expected log output on startup (note the `init` line fires BEFORE Jetty is ready for connections):

```
HelloServlet — HelloServlet.init() — greeting='Hello', loadOnStartup=1 → init fires at boot
...
Started @<ms>ms in <ms>ms
[INFO] Started Jetty Server
```

---

## 🧪 What to try

### 1. The basic happy path

```bash
curl -i http://localhost:8080/hello
```

Expected:
```
HTTP/1.1 200 OK
X-Request-Count: 1
Content-Type: text/plain;charset=UTF-8
Content-Length: 49

Hello, world!
(handled by qtp...-22, request #1)
```

Check the `X-Request-Count` header — it should increment on every subsequent call. **The fact that this header is correctly attached proves it was set BEFORE the body started flushing.**

### 2. Query parameter (the raw API, no `@RequestParam`)

```bash
curl http://localhost:8080/hello?name=kapil
```

```
Hello, kapil!
```

### 3. Concurrent requests — see the thread pool in action

```bash
# Fire 50 parallel requests and watch the thread names in the server log
seq 1 50 | xargs -P 50 -I {} curl -s http://localhost:8080/hello?name=req{} > /dev/null
```

In the server log, you'll see lines like:

```
HelloServlet — doGet — request #1 on thread 'qtp1234-22' — URI=/hello, query=name=req5
HelloServlet — doGet — request #2 on thread 'qtp1234-19' — URI=/hello, query=name=req3
HelloServlet — doGet — request #3 on thread 'qtp1234-25' — URI=/hello, query=name=req8
...
```

Multiple `qtp*` thread names = Jetty's worker pool distributing requests across threads.

The counter is monotonically increasing (1, 2, 3, ...) — proving `AtomicInteger.incrementAndGet()` is race-free.

### 4. Watch `destroy()` fire on shutdown

Hit `Ctrl-C` in the Maven terminal. Expected log:

```
HelloServlet — HelloServlet.destroy() — total requests handled: 51
```

> The number should match how many requests you fired. If it doesn't, the counter raced — but it won't, because `AtomicInteger` is thread-safe.

---

## 🐛 Now break it (the learning exercise)

### Exercise A — make the counter unsafe

In `HelloServlet.java`, replace:

```java
private final AtomicInteger requestCount = new AtomicInteger(0);
```

with:

```java
private int requestCount = 0;
```

And replace the increment line:

```java
int n = requestCount.incrementAndGet();
```

with:

```java
requestCount++;       // ← not atomic
int n = requestCount;
```

Rerun. Fire the 50-parallel-curl test. **Sometimes** the final destroy count is less than 50, or the per-request `X-Request-Count` header shows duplicates. The race is real but timing-dependent — you may need to try several times to observe it. This is exactly why "I tested it locally and it worked" is not a defense for instance-field mutation.

> Revert when done.

---

### Exercise B — write the body before setting a header

Replace the `doGet` body ordering with:

```java
try (PrintWriter out = res.getWriter()) {
    out.println(this.greeting + ", " + name + "!");
    // Now set the header — too late, response is committed
    res.setHeader("X-Request-Count", String.valueOf(n));
}
```

Rerun. Run:

```bash
curl -i http://localhost:8080/hello
```

**The `X-Request-Count` header is GONE.** No error, no log, just silently missing. This is the response-committed gotcha.

> Revert when done.

---

### Exercise C — show what `web.xml` registration looks like (compare with `@WebServlet`)

Comment out the `@WebServlet` annotation on `HelloServlet`. Then add this block to `src/main/webapp/WEB-INF/web.xml` inside `<web-app>`:

```xml
<servlet>
    <servlet-name>helloServlet</servlet-name>
    <servlet-class>kapil.spring.hello.HelloServlet</servlet-class>
    <init-param>
        <param-name>greeting</param-name>
        <param-value>Hello (from web.xml)</param-value>
    </init-param>
    <load-on-startup>1</load-on-startup>
</servlet>
<servlet-mapping>
    <servlet-name>helloServlet</servlet-name>
    <url-pattern>/hello</url-pattern>
</servlet-mapping>
```

Rerun. Same behavior, but now the registration is XML-driven instead of annotation-driven. **Both paths are equivalent at the container level — the registration goes through the same `ServletContext.addServlet()` API underneath.**

> Revert when done (or leave one path active — but never both, or the container errors with "duplicate servlet name").

---

## 🎤 Verbal self-check before moving on

Recite out loud:

> *"Tomcat / Jetty receives an HTTP request on its TCP socket. It parses the request into an `HttpServletRequest`. It looks up that `/hello` is mapped to `HelloServlet`. It pulls a thread from its worker pool. On that thread, it calls `HelloServlet.service(req, res)`. Because `HelloServlet` extends `HttpServlet`, the inherited `service()` checks `req.getMethod()`, sees `GET`, and dispatches to my `doGet()`. My code increments a counter, reads a parameter, sets status + headers, and writes the body via the writer. The try-with-resources flushes the writer, which causes the container to flush the response to the socket."*

If that sentence flows without pause, Day 2 is internalized.

---

## 🔗 Companion files

- **DeepDive (full mental model):** `../../../DeepDive/01-web-servlet-foundation.md` (Part C)
- **Reference (cheatsheet):** `../../../Reference/02-servlet-api-reference.md`
- **Growing app v1 (the `/orders/{id}` endpoint):** `../../growing-app/v1-servlet/`
