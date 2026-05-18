# Day 1 — Web Fundamentals: What's Actually Running Behind `@RestController`?

> **Track context:** Day 1 of the 10-hour Spring foundation plan (`../spring-10-hour-plan.md`). Reading-only day — no Java code yet, but a 5-minute hands-on with `nc` and `curl` at the end so you *see* HTTP with your own eyes.

---

## 📖 Prerequisites

- Basic Java (you have this)
- Vague awareness that HTTP exists and is "how the web works"
- **No Spring knowledge needed** — this note is the foundation for everything that follows

If you've shipped `@RestController` endpoints in your app but couldn't answer *"what runs between curl and my method?"* — this note is for you.

---

## 🎯 The question this note answers

You type:

```
curl http://localhost:8080/orders/123
```

…and 200ms later you get JSON back. **What happened between those two moments?**

If your answer is "Spring did it" — that's the gap this note closes. There are at least four distinct layers between curl and your `@GetMapping` method, and *all four exist whether you're using Spring Boot or not*. Spring just hides them well.

---

## 🧠 Mental model — the one paragraph

> HTTP is **text over a TCP socket**. A *web server* is "a program that opens a socket on a port, accepts connections, parses the text, and decides what to do." A *servlet container* (Tomcat, Jetty, Undertow) is a specific kind of web server that runs **Java code** — it parses HTTP for you, hands you typed request/response objects, and calls a method on your Java class. That Java class is a *servlet*. Every `@RestController` you've written in your app is a servlet in disguise.

If you remember nothing else from Day 1, remember those four sentences.

---

## 🪜 Build-up from zero — four layers

The journey from `curl` to your code has four layers. We'll peel them apart one at a time.

### Layer 1 — HTTP is just text

When `curl http://localhost:8080/orders/123` runs, it sends this **plain text** over a network connection:

```
GET /orders/123 HTTP/1.1
Host: localhost:8080
User-Agent: curl/8.4.0
Accept: */*

```

That's it. That's HTTP. Notice:

- First line: **request line** = method + path + protocol version
- Then: **headers** = `Key: Value` pairs, one per line
- Then: **blank line** = "headers done"
- Then: **optional body** (for POST/PUT — empty for GET)

The server replies with the same shape:

```
HTTP/1.1 200 OK
Content-Type: application/json
Content-Length: 42

{"id":123,"item":"book","status":"shipped"}
```

- First line: **status line** = protocol + status code + reason
- Headers
- Blank line
- Body

**That's the whole protocol.** No magic. Just text with a known shape.

> 🧾 **One-line takeaway:** HTTP = "structured text with a request/response shape, sent over a TCP socket."

#### HTTP methods (verbs) — what each one promises

The first word in the request line is the **method** (or "verb"). Each verb is a *contract* the client and server agree on:

| Method | Safe? | Idempotent? | Has body? | Typical Spring annotation |
| --- | --- | --- | --- | --- |
| **GET** | ✅ Yes (read-only) | ✅ Yes | No | `@GetMapping` |
| **HEAD** | ✅ Yes | ✅ Yes | No | (rare; same as GET but headers only) |
| **OPTIONS** | ✅ Yes | ✅ Yes | No | Used by browsers for CORS preflight |
| **POST** | ❌ No | ❌ No | Yes | `@PostMapping` — create resource, run command |
| **PUT** | ❌ No | ✅ Yes | Yes | `@PutMapping` — replace resource (full update) |
| **PATCH** | ❌ No | ❌ No (in spec) | Yes | `@PatchMapping` — partial update |
| **DELETE** | ❌ No | ✅ Yes | Sometimes | `@DeleteMapping` |

- **Safe** = "calling it has no side effects on the server" — caches and crawlers rely on this.
- **Idempotent** = "calling it 5 times has the same effect as calling it once" — clients can retry without fear.

> ❌ **COMMON MISTAKE:** Using `GET` for an endpoint that mutates state (e.g., `GET /orders/123/cancel`). Browsers, proxies, and pre-fetchers may call GETs *speculatively* — leading to accidental cancellations. **Mutating operations must be POST/PUT/PATCH/DELETE.**

#### HTTP status codes — the five families

The first line of the response is the **status line**. Codes are 3 digits, grouped by first digit:

| Family | Meaning | Common examples |
| --- | --- | --- |
| **1xx** | Informational (in progress) | `100 Continue`, `101 Switching Protocols` (rare) |
| **2xx** | Success | `200 OK`, `201 Created`, `204 No Content` (no body), `202 Accepted` (async) |
| **3xx** | Redirect | `301 Moved Permanently`, `302 Found`, `304 Not Modified` (cache hit) |
| **4xx** | Client error (you sent something wrong) | `400 Bad Request`, `401 Unauthorized`, `403 Forbidden`, `404 Not Found`, `409 Conflict`, `429 Too Many Requests` |
| **5xx** | Server error (we screwed up) | `500 Internal Server Error`, `502 Bad Gateway`, `503 Service Unavailable`, `504 Gateway Timeout` |

**Why the family matters:** load balancers, monitoring dashboards, and retry logic key off the family digit. A retry-on-failure client typically retries `5xx` and `429` but **not** `4xx` (because retrying a malformed request won't fix it).

> 🐞 **Bug magnet:** returning `200 OK` with `{"error": "..."}` in the body. The client's monitoring sees "all 200s" but users are failing. **Use the right status code** — `400` for bad input, `404` for missing, `500` for "we crashed." Spring makes this easy: `ResponseEntity.status(HttpStatus.NOT_FOUND).body(...)` or throw an exception mapped via `@ResponseStatus`.

#### Headers worth knowing by heart

Headers are `Key: Value` pairs that describe the request/response. The ones you'll see daily:

| Header | Direction | What it does |
| --- | --- | --- |
| `Host` | Request | Which virtual host on the server. **Mandatory in HTTP/1.1** — one IP can serve many hostnames; this header disambiguates. |
| `Content-Type` | Both | MIME type of the body — `application/json`, `application/x-www-form-urlencoded`, `multipart/form-data`, `text/html`, `text/plain`. Spring uses this to pick a `HttpMessageConverter`. |
| `Content-Length` | Both | Size of the body in bytes. **Critical for the parser** — tells the server "stop reading after N bytes; that's the body." |
| `Transfer-Encoding: chunked` | Both | Alternative to `Content-Length` — body is streamed in chunks of known size. Used when total length isn't known up-front (e.g., streaming response). |
| `Accept` | Request | What MIME types the client *prefers* in the response. `Accept: application/json` is how curl/Spring REST clients signal "give me JSON, not HTML." |
| `Authorization` | Request | Auth credentials. `Bearer <jwt>` for JWT, `Basic <base64(user:pass)>` for HTTP Basic. Spring Security parses this header in a filter. |
| `Cookie` / `Set-Cookie` | Req / Resp | Session state. `Cookie: JSESSIONID=...` from the browser; `Set-Cookie: ...` from the server to install a cookie. |
| `Connection` | Both | `Connection: keep-alive` (HTTP/1.1 default) or `Connection: close` — controls whether the TCP socket is reused for the next request (more on this in Part 2). |
| `User-Agent` | Request | Who's calling — `curl/8.4.0`, `Mozilla/...`, your service's name. |
| `X-Forwarded-For` / `X-Forwarded-Proto` | Request | Real client IP and protocol, set by a load balancer. **Important if your service sits behind an LB** (eg, in your app) — `request.getRemoteAddr()` returns the LB's IP, not the user's. Use `X-Forwarded-For` instead. |

**Why headers matter for Spring:** every header has a Spring annotation that pulls it. `@RequestHeader("Authorization")`, `@CookieValue("JSESSIONID")`. When you see `@RequestHeader` in code, picture the raw `Key: Value` line that fed it.

---

### Layer 2 — TCP socket, briefly

HTTP rides on **TCP**. TCP gives you:

- A **port** (an integer, 0-65535) — port 8080 in our example
- A **socket** — the OS abstraction for "a network connection between two endpoints"
- **Reliable, ordered delivery** of bytes — TCP guarantees what you send arrives in order (or you get an error)

You don't need to understand TCP deeply. You need to know:

- A server **binds** to a port (`bind(8080)`), then **listens** for incoming connections (`listen()`), then **accepts** connections one at a time (`accept()` returns a new socket per connection)
- That's the kernel-level dance. Every web server does it. You'll never write this code by hand in Spring — but knowing it exists demystifies the layers above.

#### Ports — why 8080, why 80, why 443?

Ports are just 16-bit integers (0–65535) the OS uses to multiplex many connections on one IP. The ranges:

| Range | Name | Use |
| --- | --- | --- |
| `0–1023` | **Well-known ports** | Reserved — only root can bind. `80` = HTTP, `443` = HTTPS, `22` = SSH, `25` = SMTP, `53` = DNS, `5432` = Postgres, `3306` = MySQL |
| `1024–49151` | Registered ports | App ports. `8080` (alt HTTP), `8443` (alt HTTPS), `9092` (Kafka), `6379` (Redis) live here. |
| `49152–65535` | Ephemeral ports | The OS hands these out to **client-side** sockets — when curl connects out, the kernel assigns it a random port in this range. |

**Why this matters:** binding to 80/443 in production requires root or a `CAP_NET_BIND_SERVICE` capability. That's why Spring Boot defaults to 8080 — no privilege needed. In production, an LB or nginx terminates 443 and forwards to your app on 8080.

#### A socket = a file descriptor

In Unix (Linux, macOS), **everything is a file**. A TCP connection is just a file descriptor — an integer the kernel maps to a connection. `accept()` returns a new file descriptor for each connection. `read()`, `write()`, `close()` work on it just like a file.

```
Process: Tomcat (PID 12345)
   ↓
File descriptors:
   fd 0  = stdin
   fd 1  = stdout
   fd 2  = stderr
   fd 3  = listening socket on port 8080
   fd 4  = accepted connection from client A
   fd 5  = accepted connection from client B
   fd 6  = open log file
   ...
```

**Why this matters in production:**
- `lsof -i :8080` shows the process holding that port (the bug-log entry for `nc` confusion uses this).
- `ulimit -n` shows the max file descriptors a process can have — exhaust these and the JVM gets `Too many open files`. Common cause: leaked HTTP connections or DB connections that never close.

#### "Address already in use" — what's really happening

When you see this error, the OS is saying "port 8080 is already bound by some process." Causes and fixes:

| Cause | How to spot it | Fix |
| --- | --- | --- |
| Your previous run didn't terminate | `lsof -ti :8080` returns a PID | `kill <PID>` or `lsof -ti :8080 \| xargs kill -9` |
| Socket stuck in `TIME_WAIT` (recent close, OS keeping port reserved) | `netstat -an \| grep 8080` shows `TIME_WAIT` | Wait ~60s, or set `SO_REUSEADDR` on the socket (Tomcat does this by default) |
| Another process you forgot about | `lsof -i :8080` shows it's e.g. a stray Docker container | Stop the other process |

> 💡 **`SO_REUSEADDR`:** a socket option that lets a new server bind to a port that's in `TIME_WAIT` from a recently-closed socket. Without it, you'd wait up to 4 minutes (default `TIME_WAIT` duration) between restarts. Spring Boot / Tomcat set this automatically — that's why you can hot-restart your app.

---

### Layer 3 — What if you wrote a server from scratch?

Imagine you had to build a web server in Java with **no framework**. You'd write something like this (conceptually):

```java
// Conceptual — what a hand-written Java web server looks like
public class NaiveServer {

    public static void main(String[] args) throws Exception {
        // Step 1 — bind a socket to port 8080
        ServerSocket server = new ServerSocket(8080);

        // Step 2 — accept connections forever
        while (true) {
            // accept() blocks until a client connects, then returns a new socket
            Socket client = server.accept();

            // Step 3 — handle this connection (need a thread per request!)
            new Thread(() -> handle(client)).start();
        }
    }

    private static void handle(Socket client) {
        // Step 4 — read bytes from the socket
        // Step 5 — parse those bytes into a structured HTTP request
        //          (you'd write an HTTP parser — non-trivial!)
        // Step 6 — figure out what to do (route the URL to a handler)
        // Step 7 — generate a response, write it as HTTP-formatted bytes
        // Step 8 — close the socket (or keep it alive for HTTP/1.1 reuse)
    }
}
```

**This works.** This is essentially what Node.js's HTTP server does internally, or what Go's `net/http` does. But look at how much you'd have to write:

- HTTP parser (handling chunked transfer, headers, content-length, request bodies, …)
- Thread pool (one thread per request is wasteful; you'd want a pool)
- Connection lifecycle (keep-alive, timeouts, graceful shutdown)
- URL routing (which path goes to which handler?)
- Error handling (malformed requests, partial reads, …)

**Nobody wants to write this.** That's why servlet containers exist.

#### What does "parse HTTP" actually mean?

When a worker thread holds an open socket, it has a stream of bytes. Turning those bytes into a structured request is non-trivial:

**Steps in plain English:**

1. **Read the request line** — read bytes until you hit `\r\n` (CRLF). Split on spaces → method, URI, protocol.
2. **Read headers** — keep reading lines, each terminated by `\r\n`. Each line is `Key: Value`. Stop when you see an empty line (`\r\n\r\n`).
3. **Decide how to read the body** — three cases:
   - If method is `GET`/`HEAD` → no body, you're done.
   - If `Content-Length: N` header is present → read exactly N more bytes.
   - If `Transfer-Encoding: chunked` → read chunks: each chunk starts with `<hex length>\r\n<bytes>\r\n`, ending with a `0\r\n\r\n`.
4. **Build a typed object** — assemble everything into `HttpServletRequest` so the application doesn't see raw bytes.

```java
// Conceptual HTTP parser — every container hides this
public HttpRequest parse(InputStream in) throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(in));

    // Step 1 — request line
    String requestLine = reader.readLine();
    String[] parts = requestLine.split(" ");
    String method = parts[0];
    String uri = parts[1];
    String protocol = parts[2];

    // Step 2 — headers
    Map<String, String> headers = new HashMap<>();
    String line;
    while (!(line = reader.readLine()).isEmpty()) {
        int colon = line.indexOf(':');
        headers.put(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
    }

    // Step 3 — body, gated on Content-Length
    byte[] body = new byte[0];
    if (headers.containsKey("Content-Length")) {
        int len = Integer.parseInt(headers.get("Content-Length"));
        body = in.readNBytes(len);
    }
    // (chunked transfer encoding would go here too — non-trivial!)

    return new HttpRequest(method, uri, headers, body);
}
```

**Why this matters:** when you see "malformed request" errors, somewhere in this parsing path the input didn't match expectations — usually a missing `Content-Length`, a bad encoding, or premature socket close. Tomcat's logs surface these.

#### A brief history — WAR files vs embedded containers

Two deployment models, separated by ~10 years:

| Era | How you deployed | What you wrote |
| --- | --- | --- |
| **2000s** (classical) | Build a **WAR file** (a ZIP of your servlets + `web.xml`). Drop it into a *pre-installed* Tomcat/JBoss/WebSphere on the server. Restart container to pick up the WAR. | Just your servlets and config. The container was operations' problem. |
| **2015+ (Spring Boot era)** | Build a **fat JAR** that **contains an embedded Tomcat**. Run `java -jar app.jar`. The container ships *with* your app. | Servlets + your own container. You own the runtime. |

**The mental shift:**
- Before: "I write code; ops installs/runs the container."
- Now: "I ship the container with my code; ops just runs the JAR."

This is why Spring Boot apps don't need an external Tomcat. And it's why classical Spring + WAR services (eg, `mcse_lite`-style legacy modules in your app) still exist alongside newer Spring Boot services — both patterns are alive in most large codebases.

> 🎯 **Interview hook:** "How does Spring Boot run without an external server?" — *"It embeds a servlet container (Tomcat by default) as a library. The auto-configuration starts it during `ApplicationContext` initialization, so by the time `main()` returns, Tomcat is listening on 8080."*

> ❌ **COMMON MISCONCEPTION:** "The JVM is a web server."
> ✅ **REALITY:** The JVM is just the Java runtime. It doesn't open ports or speak HTTP. A *Java application running in the JVM* (the servlet container) opens the port. The JVM is the engine; the container is the car.

---

### Layer 4 — Enter the servlet container

A **servlet container** is "the layer 3 code from above, productionized and reusable, with a clean API for your code to plug into."

The three big ones in Java:

| Container | Notes |
| --- | --- |
| **Tomcat** | The classic; Spring Boot's default; what most Java services use (eg, `mcse_lite` in your app) |
| **Jetty** | Lighter, embeddable; sometimes used for tests or microservices |
| **Undertow** | Modern, async-first; from JBoss/Red Hat; less common but a Spring Boot option |

All three do the same job:
1. Open a socket on a port
2. Parse HTTP for you
3. Hand you a typed `HttpServletRequest` and `HttpServletResponse`
4. Call a method on a Java class **you registered with the container**

That registered class is — finally — a **servlet**.

---

## 🪜 What's a servlet, really?

A servlet is a Java class that implements the **Servlet contract**:

```java
public interface Servlet {

    // Container calls this once when the servlet is created
    void init(ServletConfig config);

    // Container calls this on every request
    void service(ServletRequest req, ServletResponse resp);

    // Container calls this once when the servlet is shut down
    void destroy();

    // (Two more housekeeping methods — config + info — not interesting here)
}
```

That's the entire API. Three methods. The container calls them at the right times. Your code goes in `service()`.

**In practice nobody implements `Servlet` directly.** You extend `HttpServlet` instead — a convenience class that splits `service()` into HTTP-method-specific overrides:

```java
public class HelloServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        resp.setContentType("text/plain");
        resp.getWriter().write("Hello, world!");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        // handle POST
    }
}
```

That's a complete, runnable servlet. (Day 2 you'll deploy exactly this.)

### The thread model — this is where bugs hide

The container keeps a **thread pool** (say, 200 threads). When a request comes in:

1. A free worker thread is pulled from the pool
2. That thread calls `servlet.service(req, resp)` on the **same servlet instance** as every other thread
3. The thread is returned to the pool when `service()` returns

**Key consequence:** the same servlet object is being called by many threads in parallel.

```java
public class CounterServlet extends HttpServlet {

    // ❌ COMMON MISTAKE — instance field shared across threads
    private int counter = 0;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        counter++;                          // ❌ race condition under concurrent load
        resp.getWriter().write("count: " + counter);
    }
}
```

```java
public class CounterServlet extends HttpServlet {

    // ✅ CORRECT — use a thread-safe primitive
    private final AtomicInteger counter = new AtomicInteger();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        int n = counter.incrementAndGet(); // ✅ atomic
        resp.getWriter().write("count: " + n);
    }
}
```

**Why this matters for Spring:** every `@RestController` is, under the hood, a single bean (singleton scope by default). Multiple threads call your `@GetMapping` methods on the *same instance* concurrently. Same rule applies — **no mutable state in fields**.

> 🐞 **The bug to remember:** instance fields in a servlet (or `@RestController`) = data leaks across requests. Use local variables or thread-safe primitives.

#### What "the thread is blocked" actually means at the OS level

When your controller does `orderRepository.findById(id)`, the JDBC driver sends bytes to the DB and then calls `read()` on the DB socket — which **blocks** waiting for the response. At the OS level, the thread's state flips from `RUNNABLE` to `WAITING` (or `BLOCKED`). The kernel parks it — no CPU is used — and only un-parks it when the DB socket has data to read.

**Two key consequences:**
1. **CPU isn't the bottleneck during I/O waits.** You can have all 200 worker threads parked, CPU at 5%, and yet no capacity for new requests.
2. **A thread is ~1 MB of stack memory.** 200 threads = 200 MB of stack alone. Cranking `maxThreads` to 2000 isn't free — it costs memory and increases context-switching.

This is the foundation for understanding async servlets and WebFlux later.

#### Preview — async servlets (Servlet 3.0+)

The classical model: one worker thread held for the entire request, even while waiting on I/O. **The waste:** thread sitting idle while DB does work.

Servlet 3.0 (2009) added a way out: `request.startAsync()`. The pattern:

**Steps in plain English:**

1. **Worker thread starts the request** — same as before.
2. **Inside the handler, call `request.startAsync()`** — this tells the container "I'm not done yet, but release me."
3. **Worker thread returns to the pool immediately** — available for the next request.
4. **Later, when I/O completes** (e.g., a callback from a non-blocking DB driver), a *different* thread writes the response and calls `asyncContext.complete()`.

```java
// Conceptual — Servlet 3.0 async pattern
@Override
protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
    AsyncContext asyncCtx = req.startAsync();

    // Kick off non-blocking work on some other thread/executor
    asyncDb.fetchOrder(id).whenComplete((order, err) -> {
        // This runs on a DIFFERENT thread when the DB returns
        resp.getWriter().write(serialize(order));
        asyncCtx.complete();
    });

    // doGet returns immediately — worker thread is freed
}
```

**Spring exposes this via:** returning `CompletableFuture<T>`, `DeferredResult<T>`, or `Callable<T>` from a `@Controller` method. Spring handles `startAsync()` for you.

**Why it matters:** for I/O-heavy workloads, async lets a handful of threads serve thousands of concurrent requests because no thread is parked waiting. This is the bridge to the reactive (WebFlux) model — same idea, taken further with non-blocking I/O all the way down.

> 🎯 **For your 30-day interview window:** know that this exists and the basic shape. We're staying on classical blocking servlets for the 10-hour track; reactive is a Tier 2 deep dive.

---

## 🎨 Visual — the full request journey

```
                    curl http://localhost:8080/orders/123
                                  │
                                  ▼
                  ┌───────────────────────────────────┐
                  │ Layer 2: TCP                      │
                  │   • Open socket to 127.0.0.1:8080 │
                  │   • Send bytes                    │
                  └───────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────┐
│           Layer 4: Servlet Container (Tomcat / Jetty)           │
│                                                                 │
│   ┌─────────────────────────────────────────────┐               │
│   │ Acceptor thread                             │               │
│   │   • server.accept() returns a new Socket    │               │
│   │   • Hand off to worker pool                 │               │
│   └─────────────────────────────────────────────┘               │
│                       │                                         │
│                       ▼                                         │
│   ┌─────────────────────────────────────────────┐               │
│   │ Worker thread N (one of 200 in the pool)    │               │
│   │   1. Read bytes from socket                 │               │
│   │   2. Parse HTTP text  ──┐                   │               │
│   │   3. Build HttpServletRequest ◄── Layer 1   │               │
│   │   4. Look up servlet by URL                 │               │
│   │   5. Call servlet.service(req, resp)        │               │
│   │   6. Write resp back to socket as HTTP      │               │
│   └─────────────────────────────────────────────┘               │
│                       │                                         │
│                       ▼                                         │
│   ┌─────────────────────────────────────────────┐               │
│   │ Your servlet                                │               │
│   │   protected void doGet(req, resp) {         │               │
│   │     // YOUR LOGIC                           │               │
│   │   }                                         │               │
│   └─────────────────────────────────────────────┘               │
└─────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
                       [HTTP response back to client]


KEY INVARIANT:
   You only write the doGet/doPost method body.
   Everything else (TCP, HTTP parsing, threading, lifecycle)
   is the container's job. That's the entire point of a container.
```

---

## 🔬 Optional 5-minute hands-on — see HTTP as text

This costs five minutes and pays off forever. You don't need Java, Maven, or any setup beyond `nc` and `curl` (both come pre-installed on macOS/Linux).

### Steps

**Terminal A** — start a "dumb server" that just shows whatever bytes arrive:

```
nc -l 8080
```

(`nc` = netcat. It listens on a TCP port and prints whatever it receives. It doesn't speak HTTP — just raw bytes.)

> ⚠️ **Expected behavior:** after hitting Enter, the terminal **appears stuck on a new blank line**. No "listening on 8080" output, no prompt. **This is correct.** `nc -l` silently binds to the port and waits for a connection. Don't kill it — leave it sitting there.
>
> Want to confirm it's actually listening? Open another terminal and run `lsof -i :8080` — you should see a `nc` process. ([Bug log entry #1](../spring-prep-log.md#7--running-buglesson-log) covers this — common first-time confusion.)

**Terminal B** — open a *new* terminal window/tab (don't close Terminal A), then run:

```
curl -v http://localhost:8080/hello?name=kapil
```

### What you'll see

In **Terminal A**, this raw text shows up:

```
GET /hello?name=kapil HTTP/1.1
Host: localhost:8080
User-Agent: curl/8.4.0
Accept: */*

```

That's it. **That's the HTTP request curl sent.** Plain text. A few lines. The thing every `@GetMapping` method in your app receives — that's what shows up at the TCP socket. The container parses these lines into the `HttpServletRequest` object you use; without the container, you'd have to parse this yourself.

(curl will time out because `nc` doesn't know how to send an HTTP response back. That's expected — you just wanted to *see* the request.)

> 🧾 **What this exercise teaches:** HTTP is not magic. It's structured text. A web server is "the thing that knows how to parse that text and call your code."

---

## ❌/✅ Common mistakes & misconceptions

| ❌ Wrong | ✅ Right |
| --- | --- |
| "The JVM is the web server." | The JVM is just the runtime. A Java application *running in the JVM* (the servlet container) is the web server. |
| "Web server" = "application server" = "servlet container" — they're synonyms. | Different roles. **Web server** = serves static files originally (nginx, Apache HTTPD). **Servlet container** = runs Java servlets (Tomcat, Jetty). **Application server** = full Java EE stack (older WebSphere, WildFly, etc.). In modern usage they blur, but the distinctions matter in interviews. |
| "Spring Boot doesn't use servlets." | Spring Boot *embeds* a servlet container (Tomcat by default). Every `@RestController` is dispatched by `DispatcherServlet`, which IS a servlet. Boot didn't kill servlets — it hid them. |
| "Servlet instance fields are fine because the servlet is per-request." | The servlet instance is **shared across all requests**, one per registered URL pattern. Instance fields = shared mutable state = race condition. |
| "If I run `java -jar app.jar`, the JVM serves my HTTP." | The JVM runs your `main()`. Your `main()` (if it's a Spring Boot app) creates an `ApplicationContext` which **starts the embedded Tomcat**, which is what listens on the port. Take Tomcat out of the classpath and nothing serves HTTP — you'd just have a JVM running a `main()` that does nothing visible. |

> 🐞 **Lesson learned the hard way (2026-05-18):** Conflating "JVM," "server," and "framework" hides where the actual work happens. The cleanest mental model: JVM = the engine; servlet container = the car; Spring = the dashboard/controls; your `@RestController` = the driver's input. Each layer adds abstraction. Knowing them separately is what interviewers test.

---

## 🏢 Where you've seen this in your app

Open `mcse_lite/components/cache/cache-service/.../PromiseImperiumServiceController.java` (lines 17-25):

```java
@Slf4j
@RestController
@RequestMapping(path = IMPERIUM)
public class PromiseImperiumServiceController {

    private final ImperiumServiceHandler imperiumServiceHandler;

    public PromiseImperiumServiceController(Optional<ImperiumServiceHandler> imperiumServiceHandler) {
        this.imperiumServiceHandler = imperiumServiceHandler.orElse(null);
    }

    @PostMapping("/execute/single")
    public ResponseEntity<BPMResponse> executeImperiumCall(@RequestBody Map<String, Object> payload) {
        // ... business logic
    }
}
```

**What's actually happening behind this class** (we'll fully unpack this on Day 6, but here's the preview):

1. **Tomcat** (the servlet container) is running inside the JVM
2. Tomcat has **one servlet** registered: `DispatcherServlet`
3. When a request hits `/imperium/execute/single`, Tomcat's worker thread calls `DispatcherServlet.service(req, resp)`
4. `DispatcherServlet` looks at the URL, finds `PromiseImperiumServiceController.executeImperiumCall` matches, and invokes it
5. Your method runs, returns a `ResponseEntity`, which `DispatcherServlet` serializes back to HTTP

**The point:** even though this class doesn't extend `HttpServlet`, it's reached *through* a servlet. The whole `@RestController` machinery is a Spring abstraction on top of one servlet (`DispatcherServlet`). That's the secret you've been working around for years.

---

## 🎤 Interview Q&A — what they actually ask

### Q1: "What's the difference between a web server, an application server, and a servlet container?"

> **Model answer:** Historically these were distinct. A **web server** like Apache HTTPD or nginx originally served static files — HTML, images, CSS — over HTTP. An **application server** ran server-side code; in the Java world this meant heavyweight Java EE servers like WebSphere or WildFly that bundled EJB, JMS, JTA, and everything else. A **servlet container** specifically runs Java servlets — Tomcat and Jetty are the canonical ones; they're a subset of "application server" because they only implement the servlet specification, not the full Java EE stack. In modern usage, especially with Spring Boot, the distinction blurs — we just call Tomcat a "web server" colloquially, even though technically it's a servlet container.

### Q2: "Walk me through what happens between `curl localhost:8080/orders/123` and my `@GetMapping` method getting called."

> **Model answer:** curl opens a TCP connection to port 8080 and sends a plain-text HTTP request. The servlet container — Tomcat by default in Spring Boot — has an acceptor thread that accepts the connection and hands the socket to a worker thread from its pool. The worker reads bytes, parses the HTTP into a `HttpServletRequest` object, then calls `DispatcherServlet.service(req, resp)` — because `DispatcherServlet` is the one servlet Spring registers with the container. `DispatcherServlet` then uses `HandlerMapping` to find that my `@GetMapping("/orders/{id}")` method matches the URL, and `HandlerAdapter` to actually invoke it with the path variable extracted. My method runs, returns an object; Spring's `HttpMessageConverter` serializes it to JSON; the bytes are written back through the socket. The connection is either closed or kept alive for the next request.

### Q3: "Why is storing request state in a `@RestController` instance field a bug?"

> **Model answer:** Spring beans, including `@RestController`s, are singleton-scoped by default. The container creates ONE instance and shares it across all threads. Tomcat's worker pool calls methods on that single instance concurrently — typically up to 200 simultaneous threads. So any mutable instance field is shared mutable state without synchronization, which means race conditions and data leaks across user requests. The fix is to never store per-request state in fields — use method-local variables, request attributes, or thread-safe primitives like `AtomicInteger` if you genuinely need cross-request state at the controller level.

### Q4: "Is Spring Boot a web server?"

> **Model answer:** Not quite — Spring Boot is a framework, not a server. But Spring Boot **embeds** a servlet container (Tomcat by default; Jetty or Undertow if you swap the starter). So when you `java -jar app.jar` a Boot application, what's listening on port 8080 is the embedded Tomcat that Boot's auto-configuration started during application startup. Take the `spring-boot-starter-web` dependency out — no Tomcat, and your Boot app starts but doesn't listen on any port. The framework is the glue; the container does the actual HTTP listening.

---

## 🧾 TL;DR — mental hook

> **HTTP is text over TCP. A servlet container parses that text and calls your Java code. A servlet is the Java class the container calls. Every `@RestController` is a servlet in disguise — Spring's `DispatcherServlet` is the actual servlet the container sees; your controllers are routed by it.**

**Four layers, in one breath:** TCP socket → HTTP text → servlet container → your servlet (or `@RestController`).

**The bug to never forget:** servlets and `@RestController`s are singletons under concurrent load. **No mutable instance fields.**

**The hands-on you can repeat any time:** `nc -l 8080` + `curl -v http://localhost:8080/anything` — see raw HTTP text in 5 seconds.

---

## 📚 What's next

**Day 2** — Servlet API + the first runnable code. You'll:
- Build a hello-world servlet from scratch (no Spring)
- Run it on embedded Jetty
- Build `v1` of the growing app (`/orders/{id}` endpoint, raw servlet)
- See `web.xml` (the original "URL-to-servlet" registry)

**Cross-references:**
- Bigger picture: `../spring-10-hour-plan.md` (Day 1 entry)
- This will connect to: Day 6 (`DeepDive/06-spring-mvc.md` — DispatcherServlet internals)
- Universal style rules: `../../../AGENTS.md`
- JavaBackend subdomain rules: `../../AGENTS.md`

---
---

# Part 2 — Deeper Mental Model (the second-hour material)

> **Why this part exists:** Part 1 gave you the four-layer model (TCP → HTTP → container → servlet). Part 2 gives you the **movie of a request** so you can close your eyes and visualize what happens between curl and your `@Controller`. After Part 2, you can answer interview questions like *"explain the full request lifecycle in a Spring Boot app"* without stalling.

---

## 🎯 Goal of Part 2

By the end of this section, you should be able to:

1. **Narrate the 6 stages** of a request from DNS lookup to TCP close (or keep-alive)
2. **Dissect `HttpServletRequest`** field-by-field, knowing where each piece came from in the raw HTTP bytes
3. **Explain the filter chain** — the missing layer between "container received request" and "your `@Controller` ran"
4. **Articulate the blocking thread problem** — why Tomcat's ~200-thread default limits concurrent requests, and why slow DB queries hang services

These four capabilities together = the mental model of "what's happening behind a Spring Boot request."

---

## 🪜 Section 1 — The 6-stage packet journey

When you type `curl http://api.example.com/orders/123` and hit Enter, **six distinct things** happen in order. Most engineers can name 2-3 of them. After this section, you'll name all six.

### Stage overview

```
Stage 1 — DNS resolution         (api.example.com → 1.2.3.4)
Stage 2 — TCP 3-way handshake    (SYN / SYN-ACK / ACK)
Stage 3 — TLS handshake          (only if HTTPS)
Stage 4 — HTTP request           (your bytes go up)
Stage 5 — HTTP response          (server's bytes come back)
Stage 6 — Close OR keep-alive    (TCP FIN / or reuse for next request)
```

Now each one:

---

### Stage 1 — DNS resolution (the "what's the IP?" lookup)

Your machine doesn't know what `api.example.com` means. It asks a DNS resolver: *"What's the IP address for this hostname?"* The resolver returns something like `1.2.3.4`. Now your machine can route packets.

**Why it matters for Spring:** when your Spring Boot app calls another service via `RestTemplate`, the same DNS lookup happens. DNS misconfiguration or stale caches are a real production cause of "service can't reach dependency."

> 🔎 **Quick observable:** `dig api.example.com` (or `nslookup`) shows you exactly what the resolver returned. Worth running once to see DNS isn't abstract — it's a lookup table.

---

### Stage 2 — TCP 3-way handshake (establishing the connection)

Before sending HTTP, the OS opens a TCP connection. TCP does this with a **3-message dance**:

```
Client                                   Server (1.2.3.4:80)
  │                                        │
  │  ─── SYN  (seq=X)                ───▶  │   "Want to talk"
  │                                        │
  │  ◀── SYN-ACK  (seq=Y, ack=X+1)  ────   │   "Sure, here's my seq"
  │                                        │
  │  ─── ACK  (ack=Y+1)              ───▶  │   "Got it, let's go"
  │                                        │
  │ ◄═══════ Connection ESTABLISHED ═════▶ │
  │                                        │
```

**Three messages = "3-way handshake."** Both sides agree on starting sequence numbers, then the connection is open. Until this completes, **no HTTP can flow**.

#### The 4-tuple — what uniquely identifies a connection

A TCP connection is identified by **four values**, not two:

```
(client IP, client port, server IP, server port)
```

- Server port is **fixed** (e.g., 8080).
- Server IP is **fixed**.
- Client IP is **fixed** (the calling machine).
- **Client port is the variable** — the kernel picks a random ephemeral port (49152–65535) for each outbound connection.

This is how one server on `:8080` handles thousands of simultaneous connections: each connection's 4-tuple is unique because each client uses a different ephemeral port. The kernel demultiplexes incoming packets to the right socket using the 4-tuple as the key.

> 💡 **Tied to `TIME_WAIT` (Stage 6):** because ephemeral ports are a finite resource, accumulating closed-but-still-`TIME_WAIT` connections from the *client side* can exhaust ports. We'll come back to this.

> ⚠️ **Real-world consequence:** every new TCP connection costs at least 1 round-trip time (RTT). For a service on the other side of the country (50ms RTT), opening a fresh TCP connection costs ~50ms just for the handshake. That's why connection pooling and HTTP/1.1 keep-alive matter — you don't want to re-handshake for every request.

---

### Stage 3 — TLS handshake (if HTTPS)

For `https://` URLs, **after** TCP is established, another handshake runs on top:

1. Client sends `ClientHello` with supported cipher suites + a random number
2. Server responds with `ServerHello`, its TLS certificate, and its random number
3. Client verifies the certificate (validates the chain back to a trusted root CA)
4. Client and server derive a shared symmetric key (via Diffie-Hellman or RSA, depending on cipher)
5. Both sides switch to encrypted communication

**Critical insight for the mental model:** **the servlet container terminates TLS.** Tomcat decrypts the bytes before parsing HTTP. Your `@Controller` **never sees encrypted bytes** — by the time bytes reach your code, they're plain text in `HttpServletRequest`.

```
Wire:          [encrypted bytes]
                       │
                       ▼
Tomcat:        [TLS layer decrypts]   ← Tomcat (or a load balancer in front of it) does this
                       │
                       ▼
Tomcat:        [plain HTTP text]
                       │
                       ▼
DispatcherServlet  →  @Controller     ← your code sees plain text
```

> 💡 **In typical production setups (eg, in your app):** TLS termination usually happens at the **load balancer** (NetScaler / nginx / Apache in front of your service), not at Tomcat. Your Spring app receives plain HTTP from the LB on an internal network. This is why the LB needs the TLS cert, not your service.

---

### Stage 4 — The HTTP request (the bytes you already know)

Now the actual HTTP text flows (the text you saw via `nc -l 8080`):

```
GET /orders/123 HTTP/1.1
Host: api.example.com
User-Agent: curl/8.4.0
Accept: */*

```

Plain text, structured shape, ending with a blank line. Same as Part 1.

---

### Stage 5 — The HTTP response

Server processes the request and sends back:

```
HTTP/1.1 200 OK
Content-Type: application/json
Content-Length: 42
Connection: keep-alive

{"id":123,"item":"book","status":"shipped"}
```

Notice the `Connection: keep-alive` header — that's Stage 6 setting itself up.

---

### Stage 6 — Close OR keep-alive

After the response, two paths:

**Path A — Close:**
- Either side sends `TCP FIN` → 4-way close handshake → socket released
- Next request from the same client = full restart at Stage 2 (new TCP handshake)
- Expensive!

**Path B — Keep-alive (the default in HTTP/1.1):**
- Connection stays open
- Next request reuses the same TCP socket
- Skips Stages 1-3 entirely
- Massive performance win

```
Without keep-alive (HTTP/1.0 default):
  Req 1: DNS + TCP + (TLS) + HTTP + close          ← 4 round trips
  Req 2: DNS + TCP + (TLS) + HTTP + close          ← 4 round trips again
  Req 3: DNS + TCP + (TLS) + HTTP + close          ← ...

With keep-alive (HTTP/1.1 default):
  Req 1: DNS + TCP + (TLS) + HTTP                  ← 4 round trips (first only)
  Req 2:                     HTTP                   ← 1 round trip (reuse connection!)
  Req 3:                     HTTP                   ← 1 round trip
  ...
  Eventually: close (after idle timeout)
```

> 💡 **Connection pooling** is the client-side version of keep-alive: when your Spring app calls another service via `RestTemplate`, a pool of TCP connections is maintained so most calls don't pay the handshake cost. Misconfigured connection pools (too small, or never reused) are a common production issue (eg, in your app).

#### `TIME_WAIT` — the silent socket state that bites

When a TCP connection closes, the side that called `close()` first enters the `TIME_WAIT` state and **holds the socket for ~60 seconds** (Linux default: 60s; OS-tunable). The purpose: catch stray duplicate packets from the old connection so they don't get confused with a new connection on the same port pair.

**Why this hurts production:**

- A service that opens many short-lived outbound connections (no keep-alive) accumulates thousands of `TIME_WAIT` sockets. Each consumes an ephemeral port. Exhaust the 16k-ish ephemeral range and the service literally **can't open new outbound connections** — `Cannot assign requested address` errors.
- Diagnosis: `netstat -an | grep TIME_WAIT | wc -l` → if it's >10k, you've got a connection-reuse problem.
- Fix: configure the HTTP client (Apache HttpClient, OkHttp, `RestTemplate` underneath) to use a connection pool that reuses keep-alive sockets. **Production tip (eg, in your app):** corporate-standard REST clients usually enable connection pooling by default — but verify pool size matches your concurrency.

```
Client side (your Spring app calling downstream):
  Connection 1: ESTABLISHED → close() → TIME_WAIT (60s) ← port held
  Connection 2: ESTABLISHED → close() → TIME_WAIT (60s) ← port held
  ...
  After 1000 closes in 60s: 1000 ephemeral ports tied up.
  After 30k closes/min: ephemeral port exhaustion → outbound calls fail.

Fix: keep-alive + connection pool → reuse the SAME connection for many requests.
```

> 🐞 **Lesson learned the hard way (general pattern):** Spring services that look "fine" in dev can hit `TIME_WAIT` exhaustion in load tests. The symptom — "intermittent connection refused" calling a downstream — looks like a network issue. It's actually your own service running out of source ports.

---

### 🔬 Optional 2-minute hands-on — see all 6 stages in one command

Run this:

```bash
curl -v https://www.google.com
```

The `-v` flag makes curl print every stage. You'll see something like:

```
*   Trying 142.250.x.x:443...           ← Stage 1 done (DNS resolved), Stage 2 starting
* Connected to www.google.com (142.250.x.x) port 443    ← Stage 2 complete
* ALPN: offers h2,http/1.1
* TLSv1.3 (OUT), TLS handshake, Client hello (1):       ← Stage 3 starting
* TLSv1.3 (IN), TLS handshake, Server hello (2):
* TLSv1.3 (IN), TLS handshake, Certificate (11):
* TLSv1.3 (OUT), TLS handshake, Finished (20):          ← Stage 3 complete
> GET / HTTP/2                                          ← Stage 4
> Host: www.google.com
...
< HTTP/2 200                                            ← Stage 5
< content-type: text/html; charset=ISO-8859-1
...
```

**That's all 6 stages, visible in one terminal.** Every line corresponds to one of the stages we just walked through.

---

## 🪜 Section 2 — `HttpServletRequest` dissected

Spring's `@RequestParam`, `@PathVariable`, `@RequestBody`, `@RequestHeader` — all of these pull data from one Java object: `HttpServletRequest`. To "see" what Spring is doing, you need to know what's IN that object.

### Every field maps back to a piece of raw HTTP

Given this raw HTTP request:

```
POST /orders/123?source=web HTTP/1.1
Host: api.example.com
Content-Type: application/json
Authorization: Bearer abc123
Cookie: sessionId=xyz789

{"item":"book","qty":2}
```

Here's where each piece lands inside `HttpServletRequest`:

| Raw HTTP piece | `HttpServletRequest` method | Spring annotation that pulls it |
| --- | --- | --- |
| `POST` | `getMethod()` | (none — usually inferred from `@PostMapping`) |
| `/orders/123` | `getRequestURI()` / `getPathInfo()` | `@PathVariable` for path segments |
| `123` (the path segment) | parsed from URI | `@PathVariable("id") Long id` |
| `?source=web` | `getQueryString()` / `getParameter("source")` | `@RequestParam("source") String source` |
| `Host: api.example.com` | `getHeader("Host")` | `@RequestHeader("Host")` |
| `Content-Type: application/json` | `getContentType()` / `getHeader("Content-Type")` | Spring uses this to pick `HttpMessageConverter` (e.g., Jackson for JSON) |
| `Authorization: Bearer abc123` | `getHeader("Authorization")` | `@RequestHeader("Authorization")` — or extracted by Spring Security filter |
| `Cookie: sessionId=xyz789` | `getCookies()` | `@CookieValue` |
| `{"item":"book","qty":2}` (body) | `getInputStream()` / `getReader()` | `@RequestBody Order order` (Jackson deserializes from the stream) |

**Every Spring annotation on a controller method is sugar over a `HttpServletRequest` getter call.** When you write `@RequestParam("source") String source`, Spring is calling `request.getParameter("source")` and binding the result to your parameter.

### 🔬 Worked trace — same curl, same controller, every annotation

This is the exercise that locks the mental model. Take this concrete `curl`:

```bash
curl -X POST 'http://api.example.com/orders/123?source=web' \
     -H 'Content-Type: application/json' \
     -H 'Authorization: Bearer abc123' \
     -H 'X-Request-Id: req-9f2a' \
     --cookie 'sessionId=xyz789' \
     -d '{"item":"book","qty":2}'
```

The raw HTTP bytes that hit Tomcat's socket:

```
POST /orders/123?source=web HTTP/1.1
Host: api.example.com
User-Agent: curl/8.4.0
Accept: */*
Content-Type: application/json
Authorization: Bearer abc123
X-Request-Id: req-9f2a
Cookie: sessionId=xyz789
Content-Length: 23

{"item":"book","qty":2}
```

The Spring controller that handles it:

```java
@RestController
@RequestMapping("/orders")
public class OrderController {

    @PostMapping("/{id}")
    public ResponseEntity<Order> updateOrder(
            @PathVariable("id") Long id,                              // ← 123
            @RequestParam("source") String source,                    // ← "web"
            @RequestHeader("Authorization") String auth,              // ← "Bearer abc123"
            @RequestHeader(value = "X-Request-Id", required = false)
                    String requestId,                                 // ← "req-9f2a"
            @CookieValue("sessionId") String sessionId,               // ← "xyz789"
            @RequestBody OrderUpdate update                           // ← {"item":"book","qty":2} → POJO
    ) {
        // ...
    }
}
```

**Step-by-step — what Spring does for each parameter:**

1. **`@PathVariable("id") Long id` →** Spring matched the URI template `/orders/{id}` against `/orders/123`. It pulled `"123"` from the URI, then converted to `Long` via the registered `Converter<String, Long>`.
2. **`@RequestParam("source") String source` →** `request.getParameter("source")` returns `"web"` (parsed from `?source=web` query string).
3. **`@RequestHeader("Authorization") String auth` →** `request.getHeader("Authorization")` returns `"Bearer abc123"`.
4. **`@RequestHeader("X-Request-Id", required = false) String requestId` →** `request.getHeader("X-Request-Id")` returns `"req-9f2a"`. If absent, Spring passes `null` (because `required = false`).
5. **`@CookieValue("sessionId") String sessionId` →** Spring iterates `request.getCookies()` looking for one named `"sessionId"`, returns its value `"xyz789"`.
6. **`@RequestBody OrderUpdate update` →** Spring reads `request.getInputStream()` for `Content-Length` (23) bytes, looks at `Content-Type: application/json`, picks `MappingJackson2HttpMessageConverter`, and Jackson deserializes the JSON into an `OrderUpdate` POJO.

**The point:** every parameter is a getter call wrapped in type conversion. Knowing this lets you **debug missing-data bugs**: if `update` is `null`, check if `Content-Type` was actually `application/json` and the body length matches `Content-Length`. If `source` is `null`, check the query string was actually parsed. The annotation is just the contract; the work is in the underlying getter.

> 🎯 **Try this mentally for every Spring annotation you see this week.** Read a controller. Pick each `@`-prefixed parameter. Ask: *"which raw HTTP byte did this come from?"* Once you can answer reflexively, the mental model is solid.

### The three scopes — request, session, application

Beyond reading the HTTP request, `HttpServletRequest` also gives you access to three levels of state:

| Scope | API | Lifetime | Use case |
| --- | --- | --- | --- |
| **Request** | `req.setAttribute(...)` / `req.getAttribute(...)` | Single request | Pass data between filters and the controller |
| **Session** | `req.getSession().setAttribute(...)` | Across multiple requests from same user (via cookie) | User-specific state — login info, cart |
| **Application** | `req.getServletContext().setAttribute(...)` | App startup → shutdown, shared across ALL users | App-wide config, caches |

**Why this matters for Spring:**
- `@SessionAttribute` reads from the session scope
- `@RequestAttribute` reads from the request scope (attributes set by filters)
- A `@Component` bean with `@Scope("request")` is essentially application code that lives in the request scope

> 💡 **Common mistake:** trying to store per-user state in a `@Component` (which is singleton by default). The session scope or a properly-scoped bean is what you want.

---

## 🪜 Section 3 — The Filter Chain (the missing layer)

This is **the** piece Part 1 left out, and it's huge. Between "container received request" and "your `@Controller` ran," there's a chain of filters that each get to inspect/modify/short-circuit the request.

### What's a Filter?

A `Filter` is a Java class that implements:

```java
public interface Filter {
    void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException;
}
```

Three things a filter can do:
1. **Inspect/modify the request** before passing it on
2. **Call `chain.doFilter(req, resp)`** to invoke the next filter (or eventually the servlet)
3. **Inspect/modify the response** after the servlet has returned

It's the **interceptor pattern** at the HTTP level.

### A concrete example — logging filter

```java
public class RequestLoggingFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;
        long start = System.currentTimeMillis();

        // Step 1 — before-servlet logic
        log.info("→ {} {}", httpReq.getMethod(), httpReq.getRequestURI());

        // Step 2 — delegate to next filter (or eventually the servlet)
        chain.doFilter(request, response);

        // Step 3 — after-servlet logic
        long elapsed = System.currentTimeMillis() - start;
        log.info("← {} ({}ms)", ((HttpServletResponse) response).getStatus(), elapsed);
    }
}
```

This filter logs every request before and after the servlet runs. **Notice the structure:**
- Code before `chain.doFilter()` = runs *before* the servlet
- `chain.doFilter()` = "pass it down the chain"
- Code after `chain.doFilter()` = runs *after* the servlet (on the way back)

### The chain pattern visualized

```
Request comes in
       │
       ▼
┌─────────────────────────────────────────┐
│ Filter 1: RequestLoggingFilter          │
│   pre:  log "→ GET /orders"             │
│   ──── chain.doFilter() ────────────┐   │
│                                     │   │
│   ┌─────────────────────────────────▼─┐ │
│   │ Filter 2: CharacterEncodingFilter │ │
│   │   pre: set UTF-8                  │ │
│   │   ── chain.doFilter() ─────────┐  │ │
│   │                                │  │ │
│   │   ┌────────────────────────────▼┐ │ │
│   │   │ Filter 3: SecurityFilterChain│ │ │
│   │   │   pre: check auth            │ │ │
│   │   │   ── chain.doFilter() ───┐   │ │ │
│   │   │                          │   │ │ │
│   │   │   ┌──────────────────────▼┐  │ │ │
│   │   │   │ DispatcherServlet     │  │ │ │
│   │   │   │   ↓                   │  │ │ │
│   │   │   │ Your @Controller      │  │ │ │
│   │   │   │   ↓                   │  │ │ │
│   │   │   │ returns response      │  │ │ │
│   │   │   └──────────────────────┬┘  │ │ │
│   │   │                          │   │ │ │
│   │   │   post: (none here)     ◀┘   │ │ │
│   │   └────────────────────────────┬┘ │ │
│   │                                │  │ │
│   │   post: (none here)           ◀┘  │ │
│   └─────────────────────────────────┬─┘ │
│                                     │   │
│   post: log "← 200 (15ms)"        ◀─┘   │
└─────────────────────────────────────────┘
       │
       ▼
Response goes out

KEY INVARIANT:
   Filters are an ONION. Pre-logic runs outermost → inwards.
   Servlet runs in the center. Post-logic runs innermost → outwards.
```

### How do filters get into the chain?

A filter doesn't run unless it's *registered* with the container. Three registration mechanisms:

| Mechanism | When | Example |
| --- | --- | --- |
| **`web.xml` `<filter>` element** | Classical, pre-Boot. The XML lists each filter and the URL pattern it applies to. | `mcse_lite` style. |
| **`@WebFilter` annotation** | Servlet 3.0+ scan-based. Container picks it up if classpath scanning is enabled. | Less common in Spring apps. |
| **Spring `FilterRegistrationBean`** | Spring Boot way. You declare a `@Bean` of type `FilterRegistrationBean` and Boot wires it into the container's filter chain at startup, in a defined order. | The modern, ordered approach. |

```java
// Spring Boot — registering a custom filter with explicit ordering
@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<RequestLoggingFilter> loggingFilter() {
        FilterRegistrationBean<RequestLoggingFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new RequestLoggingFilter());
        bean.addUrlPatterns("/*");
        bean.setOrder(1);   // earlier = outer in the onion
        return bean;
    }
}
```

**Why ordering matters:** filter order is the order they wrap each other. Spring Security's filter must wrap your logging filter (so unauthorized requests log "denied" not "leak through"). Setting `order` controls this.

### Real-world filters you've used (without knowing)

Every Spring Boot app you've worked on (eg, in your app) has a filter chain like this:

| Filter | What it does |
| --- | --- |
| `CharacterEncodingFilter` | Forces UTF-8 on requests/responses (Spring Boot adds this automatically) |
| `HiddenHttpMethodFilter` | Lets HTML forms send PUT/DELETE via a hidden `_method` field |
| `RequestContextFilter` | Makes the current request accessible from anywhere (used by Spring internally) |
| `OncePerRequestFilter` (Spring base class) | Ensures a filter runs exactly once even if dispatched internally |
| **`SecurityFilterChain` (Spring Security)** | THE big one — Spring Security is **entirely filter-based**. Authentication, CSRF, CORS, session management — all filters in this chain. |
| `CorsFilter` | Adds CORS headers, handles preflight OPTIONS requests |

**The killer insight:** Spring Security is just a chain of filters. When you write `@PreAuthorize("hasRole('ADMIN')")`, the security check happens in a filter that runs **before** your `@Controller`. If the user isn't admin, the filter short-circuits the chain — your controller never runs.

> 🎯 **Interview gold:** "How does Spring Security work?" — *"It registers a `SecurityFilterChain` in the servlet filter chain. Every request passes through it before reaching the controller. Each filter does one job — authentication, authorization, CSRF, session — and either allows the request through (`chain.doFilter`) or short-circuits with an error response."*

### Filter vs Interceptor vs AOP — clearing up the confusion

There are three "interception points" in a Spring Boot request, and they get conflated:

| Mechanism | When it runs | Scope | Example |
| --- | --- | --- | --- |
| **Servlet Filter** | Container level, BEFORE DispatcherServlet | All HTTP requests | Security, CORS, encoding |
| **HandlerInterceptor** (Spring MVC) | After DispatcherServlet, BEFORE the `@Controller` method | Only Spring MVC handlers | Per-controller logging, locale, theme |
| **AOP `@Aspect`** | Around any Spring bean method | Any Spring bean | `@Transactional`, custom cross-cutting |

Filters are the outermost. Interceptors are inside DispatcherServlet. AOP wraps individual bean methods. We'll cover AOP fully on Day 5.

---

## 🪜 Section 4 — The blocking thread problem

This explains why your service can run at 5% CPU but still time out under load.

### Tomcat's thread pool

By default, Tomcat configures:
- `maxThreads = 200` — at most 200 worker threads in the pool
- `acceptCount = 100` — queue depth when all threads are busy
- `maxConnections = 8192` — total open TCP connections (most idle/keep-alive)

When a request arrives:
1. Acceptor thread accepts the TCP connection
2. A free worker thread from the pool of 200 picks up the request
3. Worker thread runs through filters → DispatcherServlet → your `@Controller` → response
4. Worker thread returns to the pool, ready for the next request

### The blocking problem

**The worker thread is blocked for the entire duration of your `@Controller` method.** If your method does:

```java
@GetMapping("/orders/{id}")
public Order getOrder(@PathVariable Long id) {
    return orderRepository.findById(id).orElseThrow();
    // ↑ This blocks the thread until the DB responds.
}
```

…and the DB query takes 200ms, that worker thread is **unavailable for 200ms**. With 200 threads and 5ms-per-request, Tomcat handles 200 × (1000 / 5) = **40,000 req/sec**. But if every request takes 200ms, Tomcat handles 200 × (1000 / 200) = **1,000 req/sec** — same threads, 40x worse throughput.

### What "Tomcat is full" looks like in production

```
Time T   : All 200 worker threads are busy waiting on slow DB calls.
Time T+1 : New request arrives → acceptor accepts the TCP connection → queues in acceptCount (100).
Time T+2 : 100 more requests arrive → acceptCount queue is FULL.
Time T+3 : New connections get REFUSED at the TCP level (or kernel-level backlog).
Time T+4 : Clients see "Connection refused" or huge latency. CPU is still at 5%.
```

This is why "high latency, low CPU" is a classic backend symptom — threads are blocked on I/O, not doing CPU work.

### The fixes (preview — covered later)

1. **Tune thread pool** for higher concurrency (more threads = more memory, GC pressure — not free)
2. **Connection pooling for DB calls** — already done by HikariCP (Spring Boot default), but pool size matters
3. **Async servlets** (Servlet 3.0+) — release the worker thread while waiting for I/O, resume on a different thread when ready
4. **Reactive stack (Spring WebFlux)** — completely different model. Few threads, non-blocking I/O. (Different track — defer.)

> 💡 **Production context (eg, in your app):** most classical Spring services (`mcse_lite`-style modules) run on blocking Tomcat. Knowing the thread model matters for capacity planning: "if my downstream is 100ms p99, how many threads do I need to handle 1000 req/s?" Math: `threads = req/s × latency = 1000 × 0.1 = 100 threads`. Plus headroom. This is back-of-envelope every backend engineer should be able to do.

---

## 🎨 The complete mental model — the one diagram

Imagine this picture every time you think about a Spring Boot request:

```
┌────────────────────────────────────────────────────────────────────────────────┐
│  curl http://api.example.com/orders/123                                         │
└────────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌────────────────────────────────────────────────────────────────────────────────┐
│  Stage 1: DNS resolution           api.example.com → 1.2.3.4                    │
└────────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌────────────────────────────────────────────────────────────────────────────────┐
│  Stage 2: TCP 3-way handshake      SYN → SYN-ACK → ACK                          │
└────────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌────────────────────────────────────────────────────────────────────────────────┐
│  Stage 3: TLS handshake            (terminated at LB or Tomcat)                 │
└────────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌────────────────────────────────────────────────────────────────────────────────┐
│  Stage 4: HTTP request bytes       "GET /orders/123 HTTP/1.1\nHost: ...\n\n"    │
└────────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌────────────────────────────────────────────────────────────────────────────────┐
│  Tomcat (servlet container)                                                     │
│  ┌──────────────────────────────────────────────────────────────────────────┐  │
│  │ 1. Acceptor thread: accept() returns socket                              │  │
│  │ 2. Hand off to worker thread (one of 200)                                │  │
│  │ 3. Parse HTTP text → HttpServletRequest object                           │  │
│  └──────────────────────────────────────────────────────────────────────────┘  │
│                                       │                                         │
│                                       ▼                                         │
│  ┌──────────────────────────────────────────────────────────────────────────┐  │
│  │ FILTER CHAIN                                                             │  │
│  │   ↓ CharacterEncodingFilter      (set UTF-8)                             │  │
│  │   ↓ SecurityFilterChain          (auth, CSRF, CORS)  ← Spring Security   │  │
│  │   ↓ RequestLoggingFilter         (log entry/exit)                        │  │
│  │   ↓ (any custom filters)                                                 │  │
│  └──────────────────────────────────────────────────────────────────────────┘  │
│                                       │                                         │
│                                       ▼                                         │
│  ┌──────────────────────────────────────────────────────────────────────────┐  │
│  │ DispatcherServlet (Spring MVC's one servlet)                             │  │
│  │   • HandlerMapping looks up "/orders/{id}" → OrderController.getOrder    │  │
│  │   • HandlerInterceptor.preHandle (optional)                              │  │
│  │   • HandlerAdapter invokes the method                                    │  │
│  │   • @PathVariable, @RequestParam, @RequestBody resolved from request     │  │
│  └──────────────────────────────────────────────────────────────────────────┘  │
│                                       │                                         │
│                                       ▼                                         │
│  ┌──────────────────────────────────────────────────────────────────────────┐  │
│  │ Your @Controller method                                                  │  │
│  │   public Order getOrder(@PathVariable Long id) {                         │  │
│  │     return orderService.findById(id);   ← business logic, possibly DB    │  │
│  │   }                                                                      │  │
│  │   (worker thread BLOCKS here if DB is slow)                              │  │
│  └──────────────────────────────────────────────────────────────────────────┘  │
│                                       │                                         │
│                                       ▼                                         │
│  ┌──────────────────────────────────────────────────────────────────────────┐  │
│  │ Response flow back (in reverse):                                         │  │
│  │   • HttpMessageConverter (Jackson) serializes Order → JSON               │  │
│  │   • DispatcherServlet writes JSON to HttpServletResponse                 │  │
│  │   • Filters' post-logic runs (logging, metrics)                          │  │
│  │   • Tomcat writes HTTP response bytes back to socket                     │  │
│  └──────────────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌────────────────────────────────────────────────────────────────────────────────┐
│  Stage 5: HTTP response bytes      "HTTP/1.1 200 OK\nContent-Type: ..."         │
└────────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌────────────────────────────────────────────────────────────────────────────────┐
│  Stage 6: TCP close OR keep-alive  (HTTP/1.1 default: keep-alive)               │
└────────────────────────────────────────────────────────────────────────────────┘

KEY INVARIANT:
   Every Spring Boot request flows through ALL of these layers.
   When you see @GetMapping, picture the entire pipeline above it —
   that's the mental model.
```

**Memorize this picture.** Every time you debug a Spring Boot issue — latency, security, encoding — it's somewhere in this diagram.

---

## 🎤 Interview Q&A — Part 2

### Q5: "Walk me through the full lifecycle of an HTTPS request to a Spring Boot service."

> **Model answer:** Six stages. **DNS** resolves the hostname to an IP. **TCP 3-way handshake** opens a connection — SYN, SYN-ACK, ACK. **TLS handshake** negotiates encryption — usually terminated at a load balancer in front of the service, so Tomcat sees plain HTTP on the internal network. Then the **HTTP request bytes** flow in. Tomcat's acceptor thread hands the socket to a worker thread from a pool of around 200. The worker parses HTTP into an `HttpServletRequest`, then the request runs through the **filter chain** — character encoding, Spring Security, logging, etc. After filters, **DispatcherServlet** finds the matching `@Controller` method via `HandlerMapping` and invokes it via `HandlerAdapter`, resolving `@PathVariable` / `@RequestBody` from the request. My controller method runs, returns an object. `HttpMessageConverter` (Jackson) serializes it to JSON, DispatcherServlet writes it to the response, filters' post-logic runs, and Tomcat writes the response bytes back. With HTTP/1.1 keep-alive, the TCP connection stays open for the next request.

### Q6: "What's the difference between a Servlet Filter and a Spring HandlerInterceptor? When would you use each?"

> **Model answer:** Filters operate at the **servlet container level** — they run before DispatcherServlet, on ALL HTTP requests including static resources. They're the right place for cross-cutting concerns that apply to every request: character encoding, CORS, security (Spring Security is filter-based), request logging. HandlerInterceptors live **inside DispatcherServlet** — they only run for Spring MVC handlers and get access to the matched `HandlerMethod`, so they're useful when the logic needs to know which controller method is about to run: per-controller authorization, locale resolution, theme switching. Filters use the standard Servlet API (`Filter.doFilter`); interceptors use Spring's `HandlerInterceptor` interface. If in doubt, filter is the more general tool; interceptor is the more Spring-aware one.

### Q7: "Why might my Spring Boot service show high latency but low CPU usage?"

> **Model answer:** Classic blocking-thread symptom. Tomcat's worker threads are pinned waiting on I/O — slow DB queries, slow downstream service calls — not doing CPU work. With Tomcat's default 200 thread pool, if every request takes 200ms in I/O, peak throughput is 1000 req/s regardless of CPU. New requests queue up in `acceptCount`, then get refused at the TCP level. Fixes: tune thread pool size with awareness of memory cost, ensure connection pools to downstreams are healthy, use Servlet 3.0+ async to free threads during I/O waits, or move to a reactive stack (WebFlux) for genuine non-blocking I/O. The TLS handshake and connection pooling on the client side matter too — re-handshaking per call adds up.

### Q8: "When my Spring controller has `@RequestParam("source") String source`, what's actually happening?"

> **Model answer:** Spring's `HandlerMethodArgumentResolver` for `@RequestParam` is calling `httpServletRequest.getParameter("source")` and binding the return value to the method parameter. `getParameter` reads from the parsed query string (for GET) or the form-encoded body (for POST `application/x-www-form-urlencoded`). The annotation is sugar — it's just a getter call on `HttpServletRequest` plus type conversion. Same pattern for `@PathVariable` (from URI template parsing), `@RequestHeader` (`getHeader`), `@CookieValue` (`getCookies`), and `@RequestBody` (`getInputStream` plus Jackson deserialization).

---

## 🧾 Updated TL;DR — the complete mental model

**Every Spring Boot request goes through six stages and four layers, in this order:**

1. **DNS** → IP lookup
2. **TCP** → 3-way handshake
3. **TLS** (if HTTPS) → encryption (usually terminated at LB)
4. **HTTP request** → plain text bytes
5. **Tomcat container** → acceptor + worker thread (one of 200)
6. **Filter chain** → security, encoding, logging
7. **DispatcherServlet** → handler mapping → handler adapter
8. **Your `@Controller`** → business logic (blocks worker thread during I/O)
9. **Response back** → message converter (JSON) → filters' post-logic → bytes → socket
10. **TCP close OR keep-alive** → reuse for next request

**Three big ideas:**
- `HttpServletRequest` is the source of truth — every Spring annotation reads from it
- The filter chain is where Spring Security lives — and where most "before the controller" logic belongs
- The worker thread is blocked during I/O — that's why slow DB = service hangs

**Five debugging instincts this note gave you:**
- "High latency, low CPU" → blocked threads waiting on I/O (Section 4)
- "Connection refused" outbound → `TIME_WAIT` exhaustion / no keep-alive (Stage 6)
- "Body is null in `@RequestBody`" → check `Content-Type` and `Content-Length` (Section 2 worked trace)
- "Address already in use" → previous socket still bound; verify with `lsof -i :8080` (Layer 2)
- "Spring Security blocks/allows the wrong thing" → check filter ordering (Section 3)

**One picture to remember:** the full ASCII diagram above. Everything else is decoration.

---

## 📚 Cross-references from Part 2

- **Filters in depth** — covered when we hit Spring Security in Tier 2 (post-interview)
- **DispatcherServlet internals** — Day 6 (`DeepDive/06-spring-mvc.md`)
- **AOP / `@Transactional` proxy** — Day 5 (`DeepDive/05-aop-and-proxies.md`)
- **Reactive (WebFlux) alternative** — out of scope for Tier 1; mentioned in Tier 2 roadmap
- **Connection pooling** — touched on in Day 9 (JPA / DB transactions)
