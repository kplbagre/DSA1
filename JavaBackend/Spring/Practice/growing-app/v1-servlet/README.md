# Growing app — v1 (raw servlet)

> **Day 2.** First iteration of the `GET /orders/{id}` endpoint. **Compare with `../v2-spring-mvc/` (Day 6) and `../v3-spring-boot/` (Day 7).** The pedagogy is in the side-by-side comparison.

---

## 🎯 What this version proves

**Every line of code in this servlet is something Spring eventually abstracted away.** Count what we have to write by hand:

| Concern | What v1 does | What v2 (Spring MVC) does | What v3 (Spring Boot) does |
| --- | --- | --- | --- |
| URL routing | manual `req.getPathInfo()` parsing | `@GetMapping("/orders/{id}")` | (same as v2) |
| Path variable extraction | manual `split("/")` + indexing | `@PathVariable("id") String id` | (same as v2) |
| Validation of URL shape | manual `if (segments.length != 1)` | (the binding does it) | (same as v2) |
| JSON serialization | hand-rolled `toJson()` | `@ResponseBody` + Jackson | (same as v2) |
| Error responses | manual `writeError(res, 404, msg)` | `@ExceptionHandler` or `ResponseEntity` | (same as v2) |
| Container wiring | `@WebServlet` annotation + Jetty plugin | XML / Java `@Configuration` for `DispatcherServlet` | **NOTHING — auto-configured** |
| Embedded server | `mvn jetty:run` (external plugin) | (Tomcat embedded by hand or via Boot) | (Boot starter pulls it in) |

By Day 7, the equivalent of this whole `OrderServlet.java` is a 5-line `@RestController` and a `@SpringBootApplication` class. **That delta — the ~80 lines you stop writing — is the value Spring adds.**

---

## 🚀 How to run

From this folder:

```bash
mvn jetty:run
```

The server boots on `http://localhost:8080`. Watch for the log lines:

```
OrderServlet — OrderServlet.init() — registered at /orders/*, 3 orders preloaded
[INFO] Started Jetty Server
```

---

## 🧪 What to try

### 1. Happy path

```bash
curl -i http://localhost:8080/orders/1
```

Expected:
```
HTTP/1.1 200 OK
X-Served-By: v1-raw-servlet
Content-Type: application/json;charset=UTF-8

{"id":"1","customer":"Alice","total":129.99,"status":"SHIPPED"}
```

Try `/orders/2` and `/orders/3` as well — they return different hard-coded data.

### 2. Missing id (validation path)

```bash
curl -i http://localhost:8080/orders/
```

Expected:
```
HTTP/1.1 400 Bad Request
{"error":"missing order id — usage: GET /orders/{id}"}
```

### 3. Unknown id (404 path)

```bash
curl -i http://localhost:8080/orders/999
```

Expected:
```
HTTP/1.1 404 Not Found
{"error":"no order with id=999"}
```

### 4. Wrong path shape (deliberate URL abuse)

```bash
curl -i http://localhost:8080/orders/1/details
```

Expected:
```
HTTP/1.1 400 Bad Request
{"error":"expected exactly one path segment after /orders/"}
```

> **Why this works:** the `@WebServlet` URL pattern is `/orders/*` — anything under `/orders/`. The container hands us `req.getPathInfo() = "/1/details"`, and we explicitly reject it because we only want exactly one segment. **In v2 with `@GetMapping("/orders/{id}")`, the framework does this rejection for you.**

### 5. Wrong verb

```bash
curl -i -X POST http://localhost:8080/orders/1
```

Expected:
```
HTTP/1.1 405 Method Not Allowed
```

> **Why this works:** we only overrode `doGet`. `HttpServlet`'s default `doPost` returns 405. **For free, just by not overriding.**

---

## 🪜 The lessons embedded in this code

Read `OrderServlet.java` line by line. Notice:

1. **`private final Map<String, Order> orders = Map.of(...)`** — instance field, but it's an *immutable* `Map.of` reference set once at construction. Safe to share across threads because it's effectively read-only after publication. This is the "config-only instance fields" rule from Day 2's DeepDive.

2. **`init()` log line** — fires once at boot, before any request. Proven by `loadOnStartup = 1`.

3. **`doGet`'s parse-then-validate-then-fetch-then-respond shape** — this is the universal request-handler pattern. Spring MVC just **declaratively expresses** each step instead of writing it imperatively.

4. **`writeError` helper** — we extracted error-response writing into a method to avoid duplication. v2 uses `@ExceptionHandler` for the same purpose at framework level.

5. **`try (PrintWriter out = res.getWriter())`** — try-with-resources guarantees flush. Without this, the response can come back empty in some containers.

6. **Status + content-type + headers BEFORE writing body** — set them on the response object, THEN call `getWriter().write(...)`. The opposite order silently drops the headers.

---

## 🔍 The 60-second side-by-side preview

Open this file in one window. On Day 6, you'll open `../v2-spring-mvc/src/main/java/.../OrderController.java` next to it. The v2 controller will be roughly:

```java
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final Map<String, Order> orders = Map.of(/* same as v1 */);

    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable String id) {
        Order order = orders.get(id);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(order);
    }
}
```

**Five lines of method body** vs ~30 in v1. And `Order` no longer needs `toJson()` — Jackson does that. **THAT is what `@RestController` + `@PathVariable` + `@ResponseBody` are worth.** v1 makes that worth concrete, in line counts, in this file.

---

## 🎤 Verbal self-check before moving on

Recite out loud:

> *"This is a raw servlet — `extends HttpServlet`, `@WebServlet(urlPatterns="/orders/*")`. Tomcat or Jetty parses the request, looks up the URL pattern, finds my one servlet instance, pulls a thread from the pool, and calls `service()`. The inherited `service()` checks `getMethod()`, sees GET, dispatches to my `doGet()`. Inside `doGet` I'm doing everything by hand: parse `getPathInfo()` to get the {id}, validate the path shape, look up the order, set status + content-type + headers, then hand-write JSON via `getWriter()`. By Day 7 every one of those steps will be a single annotation — but the underlying servlet contract doesn't change. `DispatcherServlet` is still a servlet."*

---

## 🔗 Companion files

- **DeepDive:** `../../DeepDive/01-web-servlet-foundation.md` (Part C)
- **Reference:** `../../Reference/02-servlet-api-reference.md`
- **Sibling exercise:** `../../exercises/01-servlet-hello/` (even simpler, single-endpoint demo)
- **Future v2 (Day 6):** `../v2-spring-mvc/` (NOT YET WRITTEN)
- **Future v3 (Day 7):** `../v3-spring-boot/` (NOT YET WRITTEN)
