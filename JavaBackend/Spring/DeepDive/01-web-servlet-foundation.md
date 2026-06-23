# Chapter 1 — Web Fundamentals & the Servlet API

> **Track context:** Chapter 1 of the Spring Foundation DeepDive series (`../spring-10-hour-plan.md`). Covers everything between "curl hits a port" and "your method runs" — HTTP/TCP foundations, the servlet container model, the Servlet API in depth, and the full request pipeline. Reading-first chapter — no production code, but a 5-minute hands-on with `nc` and `curl` that makes the whole thing concrete.

---

## 📖 Prerequisites

- Basic Java (you have this)
- Vague awareness that HTTP exists and is "how the web works"
- **No Spring knowledge needed** — this chapter is the foundation for everything that follows

If you've shipped `@RestController` endpoints in your app but couldn't answer *"what runs between curl and my method?"* — this chapter is for you.

---

## 🎯 The question this chapter answers

You type:

```
curl http://localhost:8080/orders/123
```

…and 200ms later you get JSON back. **What happened between those two moments?**

If your answer is "Spring did it" — that's the gap this chapter closes. There are at least four distinct layers between curl and your `@GetMapping` method, and *all four exist whether you're using Spring Boot or not*. Spring just hides them well.

---

# Part A — HTTP & TCP Foundations

---

## 🧠 Mental model — the one paragraph

> HTTP is **text over a TCP socket**. A *web server* is "a program that opens a socket on a port, accepts connections, parses the text, and decides what to do." A *servlet container* (Tomcat, Jetty, Undertow) is a specific kind of web server that runs **Java code** — it parses HTTP for you, hands you typed request/response objects, and calls a method on your Java class. That Java class is a *servlet*. Every `@RestController` you've written in your app is a servlet in disguise.

If you remember nothing else from this section, remember those four sentences.

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
| `Connection` | Both | `Connection: keep-alive` (HTTP/1.1 default) or `Connection: close` — controls whether the TCP socket is reused for the next request. |
| `User-Agent` | Request | Who's calling — `curl/8.4.0`, `Mozilla/...`, your service's name. |
| `X-Forwarded-For` / `X-Forwarded-Proto` | Request | Real client IP and protocol, set by a load balancer. **Important if your service sits behind an LB** — `request.getRemoteAddr()` returns the LB's IP, not the user's. Use `X-Forwarded-For` instead. |

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

In Unix (Linux, macOS), **everything is a file**. A TCP connection is just a file descriptor (an integer the kernel maps to a connection). `accept()` returns a new file descriptor for each connection. `read()`, `write()`, `close()` work on it just like a file.

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
- `lsof -i :8080` shows the process holding that port.
- `ulimit -n` shows the max file descriptors a process can have — exhaust these and the JVM gets `Too many open files`. Common cause: leaked HTTP connections or DB connections that never close.

#### "Address already in use" — what's really happening

When you see this error, the OS is saying "port 8080 is already bound by some process." Causes and fixes:

| Cause | How to spot it | Fix |
| --- | --- | --- |
| Your previous run didn't terminate | `lsof -ti :8080` returns a PID | `kill <PID>` or `lsof -ti :8080 \| xargs kill -9` |
| Socket stuck in `TIME_WAIT` (recent close, OS keeping port reserved) | `netstat -an \| grep 8080` shows `TIME_WAIT` | Wait ~60s, or set `SO_REUSEADDR` on the socket (Tomcat does this by default) |
| Another process you forgot about | `lsof -i :8080` shows it's e.g. a stray Docker container | Stop the other process |

> `SO_REUSEADDR` is a socket option that lets a new server bind to a port that's in `TIME_WAIT` from a recently-closed socket. Without it, you'd wait up to 4 minutes between restarts. Spring Boot / Tomcat set this automatically — that's why you can hot-restart your app.

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

**This works.** But look at how much you'd have to write:

- HTTP parser (handling chunked transfer, headers, content-length, request bodies, …)
- Thread pool (one thread per request is wasteful; you'd want a pool)
- Connection lifecycle (keep-alive, timeouts, graceful shutdown)
- URL routing (which path goes to which handler?)
- Error handling (malformed requests, partial reads, …)

**Nobody wants to write this.** That's why servlet containers exist.

#### What does "parse HTTP" actually mean?

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

    return new HttpRequest(method, uri, headers, body);
}
```

**Why this matters:** when you see "malformed request" errors, somewhere in this parsing path the input didn't match expectations — usually a missing `Content-Length`, a bad encoding, or premature socket close.

#### A brief history — WAR files vs embedded containers

| Era | How you deployed | What you wrote |
| --- | --- | --- |
| **2000s** (classical) | Build a **WAR file** (a ZIP of your servlets + `web.xml`). Drop it into a *pre-installed* Tomcat/JBoss/WebSphere on the server. | Just your servlets and config. The container was operations' problem. |
| **2015+ (Spring Boot era)** | Build a **fat JAR** that **contains an embedded Tomcat**. Run `java -jar app.jar`. The container ships *with* your app. | Servlets + your own container. You own the runtime. |

> 🎯 **Interview hook:** "How does Spring Boot run without an external server?" — *"It embeds a servlet container (Tomcat by default) as a library. The auto-configuration starts it during `ApplicationContext` initialization, so by the time `main()` returns, Tomcat is listening on 8080."*

---

### Layer 4 — Enter the servlet container

A **servlet container** (the long-running JVM process — Tomcat, Jetty, Undertow — that listens on a port, parses incoming HTTP, and hands parsed requests to your servlet code) is "the layer 3 code from above, productionized and reusable, with a clean API for your code to plug into."

| Container | Notes |
| --- | --- |
| **Tomcat** | The classic; Spring Boot's default; what most Java services use |
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

A servlet is a Java class that implements the **Servlet contract** (a 5-method interface — see Part C for the full deep dive):

```java
public interface Servlet {
    // Container calls this once when the servlet is created
    void init(ServletConfig config);

    // Container calls this on every request
    void service(ServletRequest req, ServletResponse resp);

    // Container calls this once when the servlet is shut down
    void destroy();
}
```

**In practice nobody implements `Servlet` directly.** You extend `HttpServlet` — a convenience class that splits `service()` into HTTP-method-specific overrides:

```java
public class HelloServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        resp.setContentType("text/plain");
        resp.getWriter().write("Hello, world!");
    }
}
```

### The thread model — this is where bugs hide

The container keeps a **thread pool** (a pre-created group of worker threads kept idle, ready to grab a request — saves the cost of creating a thread per request — say, 200 threads). When a request comes in:

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
    // AtomicInteger is Java's built-in thread-safe int wrapper; incrementAndGet() is one CPU instruction
    private final AtomicInteger counter = new AtomicInteger();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        int n = counter.incrementAndGet();
        resp.getWriter().write("count: " + n);
    }
}
```

**Why this matters for Spring:** every `@RestController` is a single bean (singleton scope — exactly one shared instance in memory, reused across all callers — by default). Multiple threads call your `@GetMapping` methods on the *same instance* concurrently. **No mutable state in fields.**

---

## 🎨 Visual — Part A request journey

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
│   │   2. Parse HTTP text  ──── Layer 1           │               │
│   │   3. Build HttpServletRequest               │               │
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

**Terminal A** — start a "dumb server" that just shows whatever bytes arrive:

```
nc -l 8080
```

> ⚠️ **Expected behavior:** after hitting Enter, the terminal **appears stuck on a new blank line**. No "listening on 8080" output, no prompt. **This is correct.** `nc -l` silently binds to the port and waits for a connection. Want to confirm it's actually listening? Open another terminal and run `lsof -i :8080`.

**Terminal B** — open a *new* terminal window/tab (don't close Terminal A), then run:

```
curl -v http://localhost:8080/hello?name=kapil
```

In **Terminal A**, this raw text shows up:

```
GET /hello?name=kapil HTTP/1.1
Host: localhost:8080
User-Agent: curl/8.4.0
Accept: */*

```

That's it. **That's the HTTP request curl sent.** Plain text. The thing every `@GetMapping` method in your app receives — that's what shows up at the TCP socket.

---

## ❌/✅ Common mistakes & misconceptions (Part A)

| ❌ Wrong | ✅ Right |
| --- | --- |
| "The JVM is the web server." | The JVM is just the runtime. A Java application *running in the JVM* (the servlet container) is the web server. |
| "Spring Boot doesn't use servlets." | Spring Boot *embeds* a servlet container (Tomcat by default). Every `@RestController` is dispatched by `DispatcherServlet`, which IS a servlet. Boot didn't kill servlets — it hid them. |
| "Servlet instance fields are fine because the servlet is per-request." | The servlet instance is **shared across all requests**, one per registered URL pattern. Instance fields = shared mutable state = race condition. |

> 🐞 **Lesson learned the hard way (2026-05-18):** Conflating "JVM," "server," and "framework" hides where the actual work happens. The cleanest mental model: JVM = the engine; servlet container = the car; Spring = the dashboard; your `@RestController` = the driver's input. Each layer adds abstraction. Knowing them separately is what interviewers test.

---

# Part B — The Deeper Request Pipeline

> This section gives you the **movie of a request** so you can close your eyes and visualize what happens between curl and your `@Controller`. After this, you can answer interview questions like *"explain the full request lifecycle in a Spring Boot app"* without stalling.

---

## 🪜 Section 1 — The 6-stage packet journey

When you type `curl http://api.example.com/orders/123` and hit Enter, **six distinct things** happen in order.

```
Stage 1 — DNS resolution         (api.example.com → 1.2.3.4)
Stage 2 — TCP 3-way handshake    (SYN / SYN-ACK / ACK)
Stage 3 — TLS handshake          (only if HTTPS)
Stage 4 — HTTP request           (your bytes go up)
Stage 5 — HTTP response          (server's bytes come back)
Stage 6 — Close OR keep-alive    (TCP FIN / or reuse for next request)
```

---

### Stage 1 — DNS resolution

Your machine doesn't know what `api.example.com` means. It asks a DNS resolver: *"What's the IP address for this hostname?"* The resolver returns something like `1.2.3.4`.

**Why it matters for Spring:** when your Spring Boot app calls another service via `RestTemplate`, the same DNS lookup happens. DNS misconfiguration or stale caches are a real production cause of "service can't reach dependency."

---

### Stage 2 — TCP 3-way handshake

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
```

Until this completes, **no HTTP can flow**. Every new TCP connection costs at least 1 round-trip time (RTT). That's why connection pooling and HTTP/1.1 keep-alive matter.

A TCP connection is identified by the **4-tuple** `(client IP, client port, server IP, server port)`. The client port is a random ephemeral port (49152–65535) the kernel picks — that's how one server on `:8080` handles thousands of simultaneous connections.

---

### Stage 3 — TLS handshake (if HTTPS)

After TCP, encryption is negotiated. **The critical insight:** the servlet container terminates TLS. Your `@Controller` **never sees encrypted bytes** — by the time bytes reach your code, they're plain text in `HttpServletRequest`.

```
Wire:          [encrypted bytes]
                       │
                       ▼
Tomcat:        [TLS layer decrypts]   ← usually the load balancer does this
                       │
                       ▼
DispatcherServlet  →  @Controller     ← your code sees plain text
```

> In typical production setups: TLS termination usually happens at the **load balancer** (in front of your service), not at Tomcat. Your Spring app receives plain HTTP from the LB on an internal network.

---

### Stage 4 & 5 — The HTTP request and response

The familiar HTTP text flows (same as Part A, Layer 1). Server processes the request and sends back the response.

---

### Stage 6 — Close OR keep-alive

**Path A — Close:** socket released, next request from same client = full restart at Stage 2.

**Path B — Keep-alive (the default in HTTP/1.1):** connection stays open, next request reuses the TCP socket. Skips Stages 1-3 entirely.

```
Without keep-alive:
  Req 1: DNS + TCP + (TLS) + HTTP + close          ← 4 round trips
  Req 2: DNS + TCP + (TLS) + HTTP + close          ← 4 round trips again

With keep-alive:
  Req 1: DNS + TCP + (TLS) + HTTP                  ← 4 round trips (first only)
  Req 2:                     HTTP                   ← 1 round trip (reuse!)
  Req 3:                     HTTP                   ← 1 round trip
```

#### `TIME_WAIT` — the silent socket state that bites

When a TCP connection closes, the side that called `close()` first holds the socket for ~60 seconds in `TIME_WAIT`. A service that opens many short-lived outbound connections accumulates thousands of these, exhausting ephemeral ports → `Cannot assign requested address` errors.

Fix: configure the HTTP client to use a connection pool that reuses keep-alive sockets.

---

## 🪜 Section 2 — `HttpServletRequest` dissected

Every Spring annotation (`@RequestParam`, `@PathVariable`, `@RequestBody`, `@RequestHeader`) pulls data from one Java object: `HttpServletRequest`.

### Every field maps back to a piece of raw HTTP

Given:

```
POST /orders/123?source=web HTTP/1.1
Host: api.example.com
Content-Type: application/json
Authorization: Bearer abc123
Cookie: sessionId=xyz789

{"item":"book","qty":2}
```

| Raw HTTP piece | `HttpServletRequest` method | Spring annotation |
| --- | --- | --- |
| `POST` | `getMethod()` | (inferred from `@PostMapping`) |
| `/orders/123` | `getRequestURI()` | — |
| `123` (path segment) | parsed from URI | `@PathVariable("id") Long id` |
| `?source=web` | `getParameter("source")` | `@RequestParam("source") String source` |
| `Authorization: Bearer abc123` | `getHeader("Authorization")` | `@RequestHeader("Authorization")` |
| `Cookie: sessionId=xyz789` | `getCookies()` | `@CookieValue("sessionId")` |
| `{"item":"book","qty":2}` (body) | `getInputStream()` | `@RequestBody Order order` (Jackson deserializes) |

**Every Spring annotation on a controller method is sugar over a `HttpServletRequest` getter call.**

When you debug `@RequestBody` returning null — check if `Content-Type` was actually `application/json`. When `@RequestParam` is null — check the query string was actually parsed. The annotation is the contract; the work is in the underlying getter.

---

### 🔬 Worked trace — curl → raw bytes → Spring controller

The most valuable thing you can do once is map a real `curl` command all the way through to the exact `HttpServletRequest` call each Spring annotation makes. Do this once; you'll never have a blank moment in an interview about request parsing again.

**The curl command:**

```bash
curl -X POST 'http://api.example.com/orders/123?source=web' \
     -H 'Content-Type: application/json' \
     -H 'Authorization: Bearer abc123' \
     -H 'X-Request-Id: req-9f2a' \
     --cookie 'sessionId=xyz789' \
     -d '{"item":"book","qty":2}'
```

**Exactly what arrives at Tomcat's socket (raw bytes — no Spring yet):**

```
POST /orders/123?source=web HTTP/1.1
Host: api.example.com
Content-Type: application/json
Authorization: Bearer abc123
X-Request-Id: req-9f2a
Cookie: sessionId=xyz789
Content-Length: 23

{"item":"book","qty":2}
```

**The Spring controller that handles it:**

```java
@RestController
@RequestMapping("/orders")
public class OrderController {

    @PostMapping("/{id}")
    public ResponseEntity<Order> placeOrder(
            // parsed from the path segment "123" in /orders/123
            @PathVariable("id") Long orderId,

            // parsed from ?source=web in the query string
            @RequestParam("source") String source,

            // read from the "Authorization: Bearer abc123" header line
            @RequestHeader("Authorization") String authHeader,

            // read from the "X-Request-Id: req-9f2a" header line
            @RequestHeader("X-Request-Id") String requestId,

            // extracted from the "Cookie: sessionId=xyz789" header
            @CookieValue("sessionId") String sessionId,

            // Tomcat calls req.getInputStream(); Jackson reads bytes → Order object
            @RequestBody Order order) {

        // orderId    = 123L
        // source     = "web"
        // authHeader = "Bearer abc123"
        // requestId  = "req-9f2a"
        // sessionId  = "xyz789"
        // order      = Order{item="book", qty=2}
        return ResponseEntity.ok(orderService.place(orderId, order));
    }
}
```

**What Spring actually calls for each annotation (no magic, just getters):**

| Spring annotation | Underlying `HttpServletRequest` call |
| --- | --- |
| `@PathVariable("id")` | URL template matched against `req.getRequestURI()`, then the segment is extracted by splitting on `/` |
| `@RequestParam("source")` | `req.getParameter("source")` → `"web"` (scans both query string AND `application/x-www-form-urlencoded` body) |
| `@RequestHeader("Authorization")` | `req.getHeader("Authorization")` → `"Bearer abc123"` |
| `@RequestHeader("X-Request-Id")` | `req.getHeader("X-Request-Id")` → `"req-9f2a"` |
| `@CookieValue("sessionId")` | `req.getCookies()` → find `Cookie` where `getName().equals("sessionId")` → `.getValue()` |
| `@RequestBody Order` | `req.getInputStream()` → read raw bytes → `ObjectMapper.readValue(stream, Order.class)` |

> 🎯 **Interview-day phrasing:** *"Every Spring annotation on a controller parameter is syntactic sugar over a `HttpServletRequest` getter. `@RequestParam` calls `getParameter()`, `@RequestHeader` calls `getHeader()`, `@CookieValue` calls `getCookies()` and searches by name, `@RequestBody` reads `getInputStream()` and hands it to Jackson. Understanding this, I can always debug annotation failures by asking 'what would the raw getter return?'"*

---

## 🪜 Section 3 — The Filter Chain (the missing layer)

Between "container received request" and "your `@Controller` ran," there's a chain of filters that each get to inspect/modify/short-circuit the request.

### What's a Filter?

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
│   │   │   │   ↓ Your @Controller  │  │ │ │
│   │   │   │   ↓ returns response  │  │ │ │
│   │   │   └──────────────────────┬┘  │ │ │
│   │   │   post: (none)          ◀┘   │ │ │
│   │   └────────────────────────────┬─┘ │ │
│   │   post: (none)                ◀┘   │ │
│   └─────────────────────────────────┬──┘ │
│   post: log "← 200 (15ms)"        ◀─┘   │
└─────────────────────────────────────────┘

KEY INVARIANT:
   Filters are an ONION. Pre-logic runs outermost → inwards.
   Servlet runs in the center. Post-logic runs innermost → outwards.
```

### Real-world filters you've used (without knowing)

| Filter | What it does |
| --- | --- |
| `CharacterEncodingFilter` | Forces UTF-8 on requests/responses (Spring Boot adds this automatically) |
| `RequestContextFilter` | Makes the current request accessible from anywhere (used by Spring internally) |
| **`SecurityFilterChain` (Spring Security)** | THE big one — Spring Security is **entirely filter-based**. Authentication, CSRF, CORS, session management — all filters in this chain. |
| `CorsFilter` | Adds CORS headers, handles preflight OPTIONS requests |

**The killer insight:** Spring Security is just a chain of filters. When you write `@PreAuthorize("hasRole('ADMIN')")`, the security check happens in a filter that runs **before** your `@Controller`. If the user isn't admin, the filter short-circuits the chain — your controller never runs.

> 🎯 **Interview gold:** "How does Spring Security work?" — *"It registers a `SecurityFilterChain` in the servlet filter chain. Every request passes through it before reaching the controller. Each filter does one job — authentication, authorization, CSRF, session — and either allows the request through (`chain.doFilter`) or short-circuits with an error response."*

### Filter vs Interceptor vs AOP

| Mechanism | When it runs | Scope | Example |
| --- | --- | --- | --- |
| **Servlet Filter** | Container level, BEFORE DispatcherServlet | All HTTP requests | Security, CORS, encoding |
| **HandlerInterceptor** (Spring MVC) | After DispatcherServlet, BEFORE the `@Controller` method | Only Spring MVC handlers | Per-controller logging, locale |
| **AOP `@Aspect`** | Around any Spring bean method | Any Spring bean | `@Transactional`, custom cross-cutting |

Filters are the outermost. Interceptors are inside DispatcherServlet. AOP wraps individual bean methods. (AOP covered in Chapter 2.)

---

## 🪜 Section 4 — The blocking thread problem

This explains why your service can run at 5% CPU but still time out under load.

### Tomcat's thread pool

By default:
- `maxThreads = 200` — at most 200 worker threads
- `acceptCount = 100` — queue depth when all threads are busy

**The worker thread is blocked for the entire duration of your `@Controller` method.** If your method does a DB call that takes 200ms, that worker thread is **unavailable for 200ms**.

- With 200 threads and 5ms-per-request: Tomcat handles 200 × (1000 / 5) = **40,000 req/sec**
- With 200ms-per-request (slow DB): 200 × (1000 / 200) = **1,000 req/sec** — 40x worse throughput

### What "Tomcat is full" looks like

```
Time T   : All 200 worker threads busy waiting on slow DB calls.
Time T+1 : New request arrives → queues in acceptCount (100).
Time T+2 : 100 more requests → acceptCount queue is FULL.
Time T+3 : New connections REFUSED at the TCP level.
Time T+4 : Clients see "Connection refused". CPU is still at 5%.
```

**"High latency, low CPU" = threads blocked on I/O.** This is the classic backend bottleneck.

**Back-of-envelope for capacity planning:** `threads needed = req/s × avg latency`. For 1000 req/s at 100ms p99: `threads = 1000 × 0.1 = 100`. Add headroom.

---

## 🎨 Visual — the complete mental model

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
│  └──────────────────────────────────────────────────────────────────────────┘  │
│                                       │                                         │
│                                       ▼                                         │
│  ┌──────────────────────────────────────────────────────────────────────────┐  │
│  │ DispatcherServlet (Spring MVC's one servlet)                             │  │
│  │   • HandlerMapping looks up "/orders/{id}" → OrderController.getOrder    │  │
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
│  │ Response flow back:                                                      │  │
│  │   • HttpMessageConverter (Jackson) serializes → JSON                     │  │
│  │   • DispatcherServlet writes to HttpServletResponse                      │  │
│  │   • Filters' post-logic runs (logging, metrics)                          │  │
│  │   • Tomcat writes HTTP response bytes back to socket                     │  │
│  └──────────────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌────────────────────────────────────────────────────────────────────────────────┐
│  Stage 5+6: HTTP response → TCP close OR keep-alive                             │
└────────────────────────────────────────────────────────────────────────────────┘

KEY INVARIANT:
   Every Spring Boot request flows through ALL of these layers.
   When you see @GetMapping, picture the entire pipeline above it.
```

---

## 🏢 Where you've seen this in your app

Open any `@RestController` in a production codebase. Every line maps 1:1 to the layers we just covered:

```java
@Slf4j
@RestController
@RequestMapping(path = "/imperium")
public class PromiseImperiumServiceController {

    private final ImperiumServiceHandler imperiumServiceHandler;

    public PromiseImperiumServiceController(Optional<ImperiumServiceHandler> handler) {
        this.imperiumServiceHandler = handler.orElse(null);
    }

    @PostMapping("/execute/single")
    public ResponseEntity<BPMResponse> executeImperiumCall(@RequestBody Map<String, Object> payload) {
        // business logic
    }
}
```

**What's actually happening:**
1. **Tomcat** (the servlet container) is running inside the JVM
2. Tomcat has **one servlet** registered: `DispatcherServlet` (Spring's front-door servlet that catches every HTTP request and dispatches it to the right `@Controller` method based on URL + verb)
3. When a request hits `/imperium/execute/single`, Tomcat's worker thread calls `DispatcherServlet.service(req, resp)`
4. `DispatcherServlet` looks at the URL, finds `executeImperiumCall` matches, and invokes it
5. Your method runs, returns a `ResponseEntity`, which `DispatcherServlet` serializes back to HTTP

**The point:** even though this class doesn't extend `HttpServlet`, it's reached *through* a servlet. The whole `@RestController` machinery is a Spring abstraction on top of one servlet.

---

# Part C — The Servlet API in Depth

> **Context:** Part A and B showed what the container does. Part C shows the contract your code fulfills — the 5-method `Servlet` interface, lifecycle in detail, `HttpServletRequest`/`HttpServletResponse` raw API, and the three ways to register a servlet.

---

## 🧠 Mental model (Part C)

> **A servlet is a contract: "give me a `(request, response)` pair and I'll do something."** The container's job is to call your `service()` method with that pair, on a thread from its pool, exactly once per HTTP request. `HttpServlet` **dispatches** (routes / forwards the request to the correct handler — like a receptionist deciding which department gets the call) `service()` to the conventional verb-named methods (`doGet`, `doPost`, ...). **Everything else in Spring MVC — `DispatcherServlet`, `@Controller`, `@RestController` — is sugar built on top of this one method.**

Three corollaries:

1. **Servlets are singletons by default.** One instance, shared across all request threads. **Instance fields are shared state.**
2. **The lifecycle is fixed.** `init()` runs once before the first request; `service()` runs N times concurrently; `destroy()` runs once at shutdown.
3. **`HttpServletRequest` and `HttpServletResponse` are the raw API.** Every Spring annotation (`@PathVariable`, `@RequestParam`, `@RequestBody`, `@ResponseBody`, `@RequestHeader`) is just a typed wrapper around a getter on these two objects.

---

## 🪜 Concept build-up

### Part 1 — The `Servlet` contract (the 5-method interface)

The Servlet specification (now `jakarta.servlet.Servlet`; pre-Jakarta-rename it was `javax.servlet.Servlet` — Jakarta EE is the Eclipse Foundation's rebrand of Java EE in 2020, which forced renaming every `javax.*` package to `jakarta.*`) defines ONE interface with **five** methods:

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

The two that matter every day: `init` and `service`.

#### What the container actually does with this interface

> **Reflective instantiation** (the technique in Step 1) means the container instantiates your servlet by *class name string* — `Class.forName(name).getDeclaredConstructor().newInstance()` — because it doesn't know your class at compile time.

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

The container owns the lifecycle and the threading. You own what happens *inside* `service()`.

> **Lesson learned the hard way (May 2026):** Forgetting that "the container owns instantiation" makes you write code like `private final List<String> recentRequests = new ArrayList<>()` inside a servlet — and then wonder why production sees `ArrayIndexOutOfBoundsException` under load. The list is shared; `ArrayList` isn't thread-safe; concurrent `add()` corrupts internal state. Fix: don't put per-request state in instance fields. Use local variables inside `service()` or a `ConcurrentHashMap` (Java's built-in thread-safe map — multiple threads can read/write simultaneously without external locking).

---

### Part 2 — `HttpServlet`: the convenience class

You almost never extend `Servlet` directly. You extend `HttpServlet` — an abstract class that **implements `service()` and dispatches (the same "dispatch" word — `HttpServlet` is the first dispatcher you meet; `DispatcherServlet` is just a fancier one) to method-specific handlers**.

#### What `HttpServlet.service()` does (simplified source)

```java
// jakarta.servlet.http.HttpServlet
public abstract class HttpServlet extends GenericServlet {

    // Container calls THIS service(req, res). It downcasts and dispatches.
    @Override
    public void service(ServletRequest req, ServletResponse res)
            throws ServletException, IOException {
        // Step 1 — downcast from generic to HTTP-aware types
        HttpServletRequest httpReq = (HttpServletRequest) req;
        HttpServletResponse httpRes = (HttpServletResponse) res;
        // Step 2 — delegate to the HTTP-aware overload
        service(httpReq, httpRes);
    }

    // The verb-dispatching service()
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
        } else {
            res.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }
    }

    // Default doGet — returns 405 unless you override
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        res.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }
}
```

By overriding `doGet` / `doPost` etc., you're plugging into a `switch (method)` that `HttpServlet` is running for you.

---

### Part 3 — The lifecycle in detail

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

`ServletConfig` (a small config object the container builds and hands you at `init` time — holds per-servlet init params plus a back-pointer to the broader `ServletContext`) gives you:
- `getInitParameter(name)` — per-servlet init params
- `getServletContext()` — application-wide context (the `ServletContext` = the per-web-app shared bag — every servlet in the same `.war` / Boot app sees the same instance)
- `getServletName()` — the registered name (useful for logging)

```java
public class MyServlet extends HttpServlet {

    // Flavor 1 — override the no-arg version (recommended)
    @Override
    public void init() throws ServletException {
        String mode = getInitParameter("mode");
    }

    // Flavor 2 — override the ServletConfig version (rarely needed)
    @Override
    public void init(ServletConfig config) throws ServletException {
        // CRITICAL: call super.init(config) first — it stores the config
        super.init(config);
        String mode = config.getInitParameter("mode");
    }
}
```

❌ **Pitfall:** if you override the `ServletConfig` version and forget `super.init(config)`, every subsequent `getInitParameter(...)` call returns `null`.

#### `service()` — the hot path

Three rules for `service()` code:
1. **No mutable instance fields without synchronization.** Local variables are fine — they live on the request thread's stack.
2. **Don't hold locks across I/O.** If you `synchronized(this)` and then call a slow DB, you've serialized all 200 threads onto one.
3. **Always finish the response.** `resp.getWriter().flush()` — some containers send empty responses if you don't.

#### `destroy()` — the polite shutdown

Called by the container when the web app is undeployed or the container is shutting down (SIGTERM — the OS's polite-shutdown signal). **NOT** called if the container crashes (`kill -9`, OOM, JVM segfault). So treat anything in `destroy()` as best-effort. Database connections and file handles **must** also be backed by a JVM shutdown-hook (a thread the JVM registers via `Runtime.getRuntime().addShutdownHook(...)` and runs during `System.exit` — best-effort cleanup hook).

---

### Part 4 — `HttpServletRequest` and `HttpServletResponse`: the raw API

#### `HttpServletRequest` — the inputs

| Category | Method | Returns | Spring equivalent |
| --- | --- | --- | --- |
| URL parts | `getRequestURI()` | `/orders/123` (path only, no query) | (auto from `@PathVariable`-bearing mapping) |
| URL parts | `getQueryString()` | `id=42&sort=asc` (raw, after `?`) | (auto from `@RequestParam`) |
| Method | `getMethod()` | `GET`, `POST`, ... | `@GetMapping` / `@PostMapping` / ... |
| Headers | `getHeader(name)` | single header value | `@RequestHeader("name")` |
| Headers | `getHeaders(name)` | all values for a repeated header | `@RequestHeader List<String>` |
| Params | `getParameter(name)` | first value of `name` from query OR form body | `@RequestParam("name")` |
| Params | `getParameterValues(name)` | all values (for `?tag=a&tag=b`) | `@RequestParam List<String>` |
| Body | `getReader()` / `getInputStream()` | raw body stream | `@RequestBody MyDto` (after deserialization) |
| Cookies | `getCookies()` | `Cookie[]` | `@CookieValue("name")` |
| Session | `getSession()` / `getSession(false)` | `HttpSession` (server-side stash for per-user data that persists across requests, identified by a session cookie) | `@SessionAttribute` |
| Attributes | `getAttribute` / `setAttribute` | per-request scratch map | `HttpServletRequest` directly |
| Identity | `getRemoteAddr()` | client IP (or proxy IP) | rare |

#### The three scopes — request, session, application

Servlet containers expose **three sharing scopes** — three buckets with different lifetimes where you can stash objects without passing them as method arguments:

| Scope | How to write | How to read | Lifetime | Use case |
| --- | --- | --- | --- | --- |
| **Request** | `req.setAttribute("user", user)` | `req.getAttribute("user")` | One HTTP request only — dies when `service()` returns | Pass data from a filter or interceptor to the controller without modifying the method signature |
| **Session** | `req.getSession().setAttribute("cart", cart)` | `req.getSession().getAttribute("cart")` | Across multiple requests from the same user — tied to a session cookie (`JSESSIONID`); expires on timeout or explicit invalidation | Login state, shopping cart, wizard step |
| **Application** | `req.getServletContext().setAttribute("rates", rates)` | `req.getServletContext().getAttribute("rates")` | App startup → shutdown — shared by ALL users and ALL threads | App-wide config caches, health counters, static lookup tables |

```java
// Request scope: set in a filter, read in controller
public class TraceFilter implements Filter {
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        // Store a generated trace ID on the request so the controller can log it
        req.setAttribute("traceId", UUID.randomUUID().toString());
        chain.doFilter(req, res);
    }
}

// Controller reads it from request scope
public class OrderController extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) {
        String traceId = (String) req.getAttribute("traceId");
        // ...
    }
}
```

> ⚠️ **Application scope is a global mutable bag** — it's shared across ALL request threads simultaneously. If you write to it after startup (not just during `init()`), you need synchronization. In Spring, prefer a `@Bean(scope=singleton)` over `ServletContext.setAttribute()` — the container manages thread safety better.

> 🎯 **Interview hook on sessions:** *"An `HttpSession` is a server-side map tied to a client by a cookie (`JSESSIONID` by default). The server creates it lazily on `getSession()` — a new session ID is generated and sent to the client via `Set-Cookie`. Subsequent requests from that client send `Cookie: JSESSIONID=...`; the container looks it up and hands back the same `HttpSession` object. Session data lives on the server; the cookie is just the lookup key."*

#### `HttpServletResponse` — the outputs

| Category | Method | What it does | Spring equivalent |
| --- | --- | --- | --- |
| Status | `setStatus(200)` | sets numeric status code | `ResponseEntity.ok()` etc. |
| Status | `sendError(404, "msg")` | short-circuits + error page | throw exception → `@ExceptionHandler` |
| Headers | `setHeader(name, value)` | sets / replaces a response header | `ResponseEntity.header(...)` |
| Headers | `setContentType("application/json")` | sets `Content-Type` | (auto from `@RequestMapping(produces=...)`) |
| Body | `getWriter()` | `PrintWriter` for text/JSON | (auto from `@ResponseBody` + Jackson) |
| Redirect | `sendRedirect(url)` | 302 to another URL | `return "redirect:/url"` from controller |

> **Critical rule about ordering:** you MUST set status, headers, and content type **before** writing any body bytes. Once a single byte is written, the response is **committed** (irreversibly sent to the wire). Calling `setHeader` after that point is silently ignored.

---

### Part 5 — Registering a servlet (three ways across history)

#### 5a. `web.xml` (Servlet 2.x — pre-2009)

```xml
<web-app>
    <servlet>
        <servlet-name>helloServlet</servlet-name>
        <servlet-class>com.kapil.demo.HelloServlet</servlet-class>
        <load-on-startup>1</load-on-startup>
    </servlet>
    <servlet-mapping>
        <servlet-name>helloServlet</servlet-name>
        <url-pattern>/hello</url-pattern>
    </servlet-mapping>
</web-app>
```

`<load-on-startup>1</load-on-startup>` = "call `init()` at container boot, not lazily on first request."

This is what `DispatcherServlet` historically got mapped to: `<url-pattern>/</url-pattern>` so it catches everything.

#### 5b. `@WebServlet` annotation (Servlet 3.0+ — 2009 onward)

```java
@WebServlet(
    name = "helloServlet",
    urlPatterns = "/hello",
    loadOnStartup = 1
)
public class HelloServlet extends HttpServlet { }
```

The container does classpath scanning (at startup, walk every `.class` file on the classpath, read its annotations, and act on them) at boot and registers `@WebServlet`-annotated classes automatically.

#### 5c. Programmatic registration via `ServletContainerInitializer` (Servlet 3.0+)

```java
public class MyInitializer implements ServletContainerInitializer {
    @Override
    public void onStartup(Set<Class<?>> handledTypes, ServletContext ctx) {
        ServletRegistration.Dynamic reg = ctx.addServlet("helloServlet", new HelloServlet());
        reg.addMapping("/hello");
        reg.setLoadOnStartup(1);
    }
}
```

This is registered via the SPI mechanism (Service Provider Interface — Java's plug-in convention where a library drops a text file at `META-INF/services/<contract-name>` containing the implementing class's FQN; the JVM auto-loads it via `ServiceLoader`).

**This is how Spring registers `DispatcherServlet`.** Spring ships `SpringServletContainerInitializer`, which finds your `WebApplicationInitializer` (Spring's plug-point interface — you implement it to declare how to build your Spring app and where to mount `DispatcherServlet`), instantiates them, and calls their `onStartup`. Spring Boot collapses this further — Boot's auto-configuration (Boot's startup-time mechanism that scans the classpath and conditionally wires beans based on what's present) just declares a `DispatcherServlet` `@Bean` and the embedded Tomcat maps it to `/`.

---

### Part 6 — Embedded vs standalone containers

#### Old model — standalone container, WAR deployment

Build a `.war` file (Web Application Archive — a zipped bundle of your compiled classes, libraries, and `web.xml`). Install Tomcat as a long-running service. Drop your WAR into `webapps/`.

#### New model — embedded container, executable JAR

Include Tomcat as a Maven dependency. Start the container from your own `main()` method. Ship one fat JAR (a single JAR file bundling your app code + all dependency JARs + an embedded container).

```java
public class Main {
    public static void main(String[] args) throws Exception {
        // Step 1 — create an embedded Jetty server bound to port 8080
        Server server = new Server(8080);

        // Step 2 — create a servlet context (the web-app boundary)
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        server.setHandler(context);

        // Step 3 — register a servlet at a URL pattern (no web.xml)
        context.addServlet(new ServletHolder(new HelloServlet()), "/hello");

        // Step 4 — start the server
        server.start();
        server.join();
    }
}
```

**This is what Spring Boot does at runtime.** The Boot starter pulls in `spring-boot-starter-tomcat`, and Boot's auto-config wires up an embedded Tomcat almost exactly like the snippet above.

**Why embedded won:**
- **Dev parity:** the dev laptop runs the same Tomcat version as prod, because it's bundled in the JAR.
- **Cloud-native fit:** Kubernetes pods (a *pod* = Kubernetes' smallest deployable unit — one or more Linux containers sharing a network namespace, treated as one logical process) want one process; `java -jar app.jar` is one process.

---

### Part 7 — Async servlets (the escape hatch from the blocking-thread problem)

> **Why this matters:** Part B, Section 4 showed that a Tomcat worker thread is blocked for the entire duration of your controller method. Async servlets let you **release the worker thread back to the pool while waiting for I/O**, then complete the response on a different thread when the result is ready.

#### The core idea

```java
// Synchronous (blocking) — worker thread pinned for entire DB call
@Override
protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
    // Worker thread blocked here for, say, 200ms
    String result = slowDbCall();
    res.getWriter().write(result);
}

// Async — worker thread released immediately; response completed later
@Override
protected void doGet(HttpServletRequest req, HttpServletResponse res) {
    // Step 1 — tell the container this request will complete asynchronously
    AsyncContext asyncCtx = req.startAsync();

    // Step 2 — kick off the slow work on another thread (e.g., a DB thread pool)
    CompletableFuture
        .supplyAsync(this::slowDbCall)          // runs on ForkJoinPool; worker thread now FREE
        .thenAccept(result -> {
            try {
                // Step 3 — when done, write the response and signal "complete"
                asyncCtx.getResponse().getWriter().write(result);
                asyncCtx.complete();             // releases the async context; socket is flushed
            } catch (IOException e) {
                asyncCtx.complete();
            }
        });
    // doGet returns HERE — worker thread goes back to the pool immediately
}
```

**The three-step async pattern in plain English:**

1. **`req.startAsync()`** — tells the container "don't close this socket when `doGet` returns; this request isn't done yet." Returns an `AsyncContext` that's your handle to complete the response later.
2. **Kick off work on another thread** — your worker thread is now free; the I/O-bound work runs on a dedicated pool (DB, HTTP client, etc.).
3. **`asyncCtx.complete()`** — signals "the response is fully written; close the socket." **Must always be called**, even on error — otherwise the socket leaks.

#### Spring's async return types

You almost never use `AsyncContext` directly in Spring. Spring MVC wraps it:

| Spring return type | What it means | Under the hood |
| --- | --- | --- |
| `DeferredResult<T>` | Result will be set from another thread later | Wraps `AsyncContext`; your code calls `deferredResult.setResult(value)` |
| `CompletableFuture<T>` | Standard Java async — set up a pipeline and return it | Spring waits for completion, then marshals the result |
| `Callable<T>` | Run this on Spring's async task executor, not Tomcat's pool | Simple; same result as blocking but frees the Tomcat thread |

```java
// DeferredResult — for event-driven (e.g., wait for Kafka message)
@GetMapping("/orders/{id}/status")
public DeferredResult<OrderStatus> getStatus(@PathVariable Long id) {
    DeferredResult<OrderStatus> result = new DeferredResult<>(5000L);  // 5s timeout

    // Kafka listener somewhere will call result.setResult(status) when event arrives
    pendingRequests.put(id, result);

    return result;  // worker thread released; response deferred
}

// CompletableFuture — for non-blocking async work
@GetMapping("/orders/{id}")
public CompletableFuture<Order> getOrder(@PathVariable Long id) {
    // asyncOrderService.findById returns CompletableFuture<Order>
    return asyncOrderService.findById(id);  // worker thread released
}
```

> **Async vs reactive (WebFlux):** Async servlets still use one thread-per-request for setup; they just release the thread during I/O. **Spring WebFlux** (Spring's reactive framework built on Reactor/Netty — non-blocking I/O from socket to controller, with no thread-per-request model) goes further — no worker thread at all, just event callbacks. For 99% of Spring MVC apps, async servlets and a well-sized thread pool are enough. WebFlux is warranted when you need very high concurrency with many simultaneous slow I/O calls.

> 🎯 **Interview-day phrasing:** *"Async servlets let a `@Controller` release the Tomcat worker thread immediately by returning a `DeferredResult` or `CompletableFuture`. The response is completed on another thread when the result is ready. This lets Tomcat's 200 threads handle far more than 200 concurrent in-flight requests, as long as the actual I/O runs on a different pool."*

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
        // read-modify-write: CPU reads current value, modifies in register, writes back
        // two threads can read the same value before either writes → undercounting + corruption
        requestCount++;
        res.getWriter().write("Request #" + requestCount);
    }
}
```

```java
// ✅ Use an atomic type
private final AtomicInteger requestCount = new AtomicInteger(0);

@Override
protected void doGet(HttpServletRequest req, HttpServletResponse res)
        throws IOException {
    // incrementAndGet() compiles to one CPU instruction — no race possible
    int n = requestCount.incrementAndGet();
    res.getWriter().write("Request #" + n);
}
```

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

```java
// ✅ try-with-resources auto-closes, which flushes
// (try-with-resources = Java 7+ syntax — anything in try(...) parens must implement AutoCloseable;
//  JVM calls close() automatically when the block exits)
@Override
protected void doGet(HttpServletRequest req, HttpServletResponse res)
        throws IOException {
    try (PrintWriter out = res.getWriter()) {
        out.write("hello");
    }
}
```

---

### Bug 3 — setting headers after the body is written

```java
@Override
protected void doGet(HttpServletRequest req, HttpServletResponse res)
        throws IOException {
    res.getWriter().write("hello");
    // ❌ Too late — response is committed. setHeader is silently ignored.
    res.setHeader("X-Trace-Id", "abc-123");
    res.setStatus(201);
}
```

```java
// ✅ Set everything BEFORE writing
@Override
protected void doGet(HttpServletRequest req, HttpServletResponse res)
        throws IOException {
    res.setStatus(201);
    res.setHeader("X-Trace-Id", "abc-123");
    res.setContentType("application/json");
    res.getWriter().write("{\"ok\":true}");
}
```

> `res.isCommitted()` returns `true` once the response has been flushed. Use this to guard defensive header-setting code.

---

### Bug 4 — overriding `init(ServletConfig)` without `super.init(config)`

```java
// ❌ Forgot to call super.init(config)
@Override
public void init(ServletConfig config) throws ServletException {
    String mode = getInitParameter("mode");  // always null — config not stored
}

// ✅ Call super, or override the no-arg init() instead
@Override
public void init(ServletConfig config) throws ServletException {
    super.init(config);
    String mode = getInitParameter("mode");  // now works
}
```

---

### Bug 5 — `synchronized(this)` on doGet

```java
// ❌ All 200 threads serialize through the same instance — catastrophic throughput
@Override
protected synchronized void doGet(HttpServletRequest req, HttpServletResponse res) {
    // If this takes 100ms, you've gone from 200 concurrent requests to 1 at a time
}
```

Fix: make your shared state thread-safe (`AtomicX`, `ConcurrentMap`), or use local variables. The servlet itself should be stateless past `init()`.

---

## 🏢 Spring vs raw servlet — a side-by-side translation

**The Spring version (what you write at work):**

```java
@RestController
@RequestMapping("/cache")
public class CacheRefreshController {

    private final CacheService cacheService;

    public CacheRefreshController(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    @PostMapping("/{name}/refresh")
    public ResponseEntity<RefreshResponse> refresh(
            @PathVariable("name") String cacheName,
            @RequestHeader("X-Service-Token") String token,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            @RequestBody RefreshRequest body) {

        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
        }
        String jobId = cacheService.triggerRefresh(cacheName, body.getKeys(), body.isAsync());
        return ResponseEntity.ok(new RefreshResponse("queued", jobId, traceId));
    }
}
```

**The raw servlet equivalent (what Spring is doing under the hood):**

```java
public class CacheRefreshServlet extends HttpServlet {

    private final ObjectMapper mapper = new ObjectMapper();
    private CacheService cacheService;

    @Override
    public void init() throws ServletException {
        // In raw servlet world, YOU wire the dependency (no DI container)
        cacheService = new CacheService();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {

        // --- @PathVariable("name") ---
        String path = req.getPathInfo();
        // path = "/someCacheName/refresh"
        String[] parts = path.split("/");
        if (parts.length < 3 || !parts[2].equals("refresh")) {
            res.sendError(404, "unknown endpoint");
            return;
        }
        String cacheName = parts[1];

        // --- @RequestHeader("X-Service-Token") ---
        String token = req.getHeader("X-Service-Token");
        if (token == null) {
            res.sendError(401, "missing auth token");
            return;
        }

        // --- @RequestHeader(value = "X-Trace-Id", required = false) ---
        String traceId = req.getHeader("X-Trace-Id");
        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
        }

        // --- @RequestBody RefreshRequest body ---
        // Jackson ObjectMapper = the standard Java JSON library — converts Java objects ↔ JSON strings
        RefreshRequest body = mapper.readValue(req.getReader(), RefreshRequest.class);

        // --- Business logic ---
        String jobId = cacheService.triggerRefresh(cacheName, body.getKeys(), body.isAsync());

        // --- ResponseEntity.ok() --- set status, content type, serialize, in the RIGHT ORDER
        RefreshResponse result = new RefreshResponse("queued", jobId, traceId);
        res.setStatus(200);
        res.setContentType("application/json");
        res.getWriter().write(mapper.writeValueAsString(result));
    }
}
```

**What the side-by-side teaches:**

1. **Every `@PathVariable` = manual `getPathInfo().split("/")`** — Spring's URL template parser saves ~5 lines per variable.
2. **Every `@RequestHeader` = `req.getHeader(name)` + null check** — Spring's `required = true` (default) gives you a 400 automatically.
3. **Every `@RequestBody` = `mapper.readValue(req.getReader(), Type.class)`** — Spring's `HttpMessageConverter` (a pluggable strategy that detects `Content-Type` and picks the right deserializer — Jackson for JSON, JAXB for XML) does this once in the framework.
4. **Every `ResponseEntity.ok(obj)` = three separate calls** — `setStatus`, `setContentType`, `getWriter().write(serialize(obj))`.
5. **Dependency injection is invisible.** The Spring version gets `CacheService` via constructor injection; the raw servlet creates it in `init()`. In a 200-endpoint app, the raw approach means 200 `init()` methods wiring dependencies by hand. That's why the IoC container exists — see Chapter 2.

> **The punchline:** your day-job controller has 15 lines. The raw servlet version has 45 lines. **That 3x gap is what Spring MVC is.** DispatcherServlet + annotations + Jackson = URL parsing + header extraction + body deserialization + response serialization, done once in the framework instead of repeated in every endpoint.

---

## 🎤 Interview Q&A

### Q1: "What's the difference between a web server, an application server, and a servlet container?"

> **Model answer:** Historically these were distinct. A **web server** like Apache HTTPD or nginx originally served static files. An **application server** ran server-side code; in the Java world this meant heavyweight Java EE servers like WebSphere or WildFly that bundled EJB, JMS, JTA, and everything else. A **servlet container** specifically runs Java servlets — Tomcat and Jetty are the canonical ones; they implement the servlet specification only, not the full Java EE stack. In modern usage, especially with Spring Boot, the distinction blurs — we just call Tomcat a "web server" colloquially, even though technically it's a servlet container.

### Q2: "Walk me through what happens between `curl localhost:8080/orders/123` and my `@GetMapping` method getting called."

> **Model answer:** curl opens a TCP connection to port 8080 and sends a plain-text HTTP request. The servlet container — Tomcat by default in Spring Boot — has an acceptor thread that accepts the connection and hands the socket to a worker thread from its pool. The worker reads bytes, parses the HTTP into a `HttpServletRequest` object, then calls `DispatcherServlet.service(req, resp)` — because `DispatcherServlet` is the one servlet Spring registers with the container. `DispatcherServlet` uses `HandlerMapping` to find that my `@GetMapping("/orders/{id}")` method matches the URL, and `HandlerAdapter` to invoke it with the path variable extracted. My method runs, returns an object; Spring's `HttpMessageConverter` serializes it to JSON; the bytes are written back through the socket.

### Q3: "Why is storing request state in a `@RestController` instance field a bug?"

> **Model answer:** Spring beans, including `@RestController`s, are singleton-scoped by default. The container creates ONE instance and shares it across all threads. Tomcat's worker pool calls methods on that single instance concurrently — typically up to 200 simultaneous threads. So any mutable instance field is shared mutable state without synchronization, which means race conditions and data leaks across user requests. The fix: never store per-request state in fields — use method-local variables, request attributes, or thread-safe primitives like `AtomicInteger` if you genuinely need cross-request state at the controller level.

### Q4: "Walk me through the full lifecycle of an HTTPS request to a Spring Boot service."

> **Model answer:** Six stages. **DNS** resolves the hostname to an IP. **TCP 3-way handshake** opens a connection — SYN, SYN-ACK, ACK. **TLS handshake** negotiates encryption — usually terminated at a load balancer in front of the service, so Tomcat sees plain HTTP on the internal network. Then the **HTTP request bytes** flow in. Tomcat's acceptor thread hands the socket to a worker thread from a pool of around 200. The worker parses HTTP into an `HttpServletRequest`, then the request runs through the **filter chain** — character encoding, Spring Security, logging, etc. After filters, **DispatcherServlet** finds the matching `@Controller` method via `HandlerMapping` and invokes it via `HandlerAdapter`. My controller method runs, returns an object. `HttpMessageConverter` (Jackson) serializes it to JSON, DispatcherServlet writes it to the response, filters' post-logic runs, and Tomcat writes the response bytes back. With HTTP/1.1 keep-alive, the TCP connection stays open for the next request.

### Q5: "What's the difference between a Servlet Filter and a Spring HandlerInterceptor?"

> **Model answer:** Filters operate at the **servlet container level** — they run before DispatcherServlet, on ALL HTTP requests. They're the right place for cross-cutting concerns that apply to every request: character encoding, CORS, security. HandlerInterceptors live **inside DispatcherServlet** — they only run for Spring MVC handlers and get access to the matched `HandlerMethod`, so they're useful when the logic needs to know which controller method is about to run. Filters use the standard Servlet API (`Filter.doFilter`); interceptors use Spring's `HandlerInterceptor` interface.

### Q6: "Why might my Spring Boot service show high latency but low CPU usage?"

> **Model answer:** Classic blocking-thread symptom. Tomcat's worker threads are pinned waiting on I/O — slow DB queries, slow downstream service calls — not doing CPU work. With Tomcat's default 200 thread pool, if every request takes 200ms in I/O, peak throughput is 1000 req/s regardless of CPU. New requests queue up in `acceptCount`, then get refused at the TCP level. Fix: tune thread pool size, ensure connection pools to downstreams are healthy, or move to async servlets / WebFlux.

### Q7: "Walk me through what happens between a `curl` request and your `doGet` being called."

> **Model answer:** curl opens a TCP connection and sends HTTP request bytes. The container's acceptor thread (a dedicated thread doing nothing but `socket.accept()` in a loop) reads from the socket and parses bytes into an `HttpServletRequest`. It then looks up which servlet is mapped to that URL via the servlet context's registry. Once it has the servlet instance, it grabs a thread from the worker pool and on that thread calls `servlet.service(req, res)`. `HttpServlet.service()` inspects the method, sees `GET`, and dispatches to `doGet(req, res)`. **My code runs.** Whatever I write to `res.getWriter()` is buffered; when `service()` returns (or I call `flushBuffer()`), the container serializes status line + headers + body and writes them to the socket.

### Q8: "Why are servlet instance fields dangerous?"

> **Model answer:** The container creates exactly ONE instance and reuses it across all request threads. Tomcat's default pool is 200 threads — so up to 200 threads can be inside the same `doGet` method on the same instance simultaneously. Any instance field they touch is shared mutable state. Unless that field is an `AtomicInteger` / `ConcurrentHashMap` / something explicitly thread-safe, I have a race condition. Local variables inside the method are fine — they live on the request thread's stack. **Rule:** instance fields are config / dependencies (set once in `init`, read-only forever after); local variables are per-request state.

### Q9: "What's `@WebServlet` and how does it relate to `web.xml`?"

> **Model answer:** Both are servlet *registration* mechanisms. `web.xml` is the classic XML declaration. `@WebServlet` (added in Servlet 3.0, 2009) is the annotation equivalent — drop it on your `HttpServlet` subclass and the container picks it up via classpath scanning at boot. In modern Spring Boot apps, neither is used directly — Spring registers `DispatcherServlet` programmatically via `WebApplicationInitializer` (which the container picks up via `ServletContainerInitializer`'s SPI). But under the hood it's the same registration API.

---

## 🧾 TL;DR — the complete mental model

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

**Five debugging instincts:**
- "High latency, low CPU" → blocked threads waiting on I/O
- "Connection refused" outbound → `TIME_WAIT` exhaustion / no keep-alive
- "Body is null in `@RequestBody`" → check `Content-Type` and `Content-Length`
- "Address already in use" → previous socket still bound; `lsof -i :8080`
- "Spring Security blocks the wrong thing" → check filter ordering

**The punchline on Spring vs raw servlet:** your 15-line `@RestController` is 45 lines of raw servlet code. Spring MVC = URL parsing + header extraction + body deserialization + response serialization, done once in the framework.

---

## 📚 Cross-references

- **Next chapter:** `02-spring-core.md` — IoC + DI + AOP + proxies (why the container exists; how `@Transactional` works)
- **DispatcherServlet internals:** covered in `03-spring-mvc-boot.md`
- **AOP / `@Transactional` proxy:** covered in `02-spring-core.md`
- **Connection pooling:** touched on in `04-jpa-transactions.md`
- **Master plan + format standards:** `../spring-10-hour-plan.md`

---

### Changelog

| Date | Change |
| --- | --- |
| 2026-05-18 | `01-web-fundamentals.md` DRAFT written. Four-layer model (TCP → HTTP → container → servlet). 5-minute nc hands-on. |
| 2026-05-18 | `02-servlet-api.md` DRAFT written. Servlet lifecycle, HttpServlet dispatch, HttpServletRequest/Response API, 3 registration mechanisms, embedded containers, 5 bug callouts, side-by-side Spring vs raw servlet. |
| 2026-05-20 | Both files received Rule 8 (First-Use Term Gloss) sweep. |
| 2026-06-10 | **Files merged into `01-web-servlet-foundation.md`** as part of 4-chapter restructure. Day 1/Day 2 framing replaced with Part A / Part B / Part C chapters. Three sections added back that the initial merge omitted: (1) `🔬 Worked trace` (curl → raw bytes → Spring annotation mapping), (2) The three scopes table (request/session/application), (3) Async servlets preview (Part 7 of Part C). Cross-references updated to point to new chapter filenames. |
