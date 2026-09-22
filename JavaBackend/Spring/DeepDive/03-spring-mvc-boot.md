# Chapter 3 — Spring MVC + Spring Boot: From DispatcherServlet to Auto-Configuration

> **Track context:** Chapter 3 of 4 in the Spring Foundation DeepDive series (`../spring-10-hour-plan.md`). Covers how Spring routes HTTP requests (DispatcherServlet + HandlerMapping), the `@Controller` / `@RestController` layer, and how Spring Boot auto-configures all of this so you don't wire it by hand. Profiles, properties, and conditional beans are covered here.

---

## 📖 Prerequisites

You should have absorbed Chapters 1 and 2:
- A servlet container creates one servlet instance per registration and calls `service()` on each request thread (Chapter 1).
- Spring's IoC container (`ApplicationContext`) creates and wires beans. `@Service`, `@Repository`, `@Controller` are discovered via `@ComponentScan` (Chapter 2).
- AOP proxies wrap beans to add cross-cutting concerns like `@Transactional`. Self-calls bypass the proxy (Chapter 2).

The question Chapter 3 answers: *"How does a URL reach your `@GetMapping` method, and what did Spring Boot automate that you used to wire by hand?"*

---

## 🧠 Mental model

> **DispatcherServlet is ONE servlet.** It catches every HTTP request. On each request it asks a `HandlerMapping` — "which `@Controller` method handles this URL + HTTP method?" The mapping returns a handler. `DispatcherServlet` invokes it via a `HandlerAdapter`, gets the return value, passes it through a `HttpMessageConverter` (Jackson for JSON), and writes the response. That's the entire Spring MVC flow.
>
> **Spring Boot auto-configures all of this.** `@SpringBootApplication` = `@Configuration` + `@ComponentScan` + `@EnableAutoConfiguration`. The last one scans the classpath, sees `spring-webmvc` and `jackson-databind`, and auto-registers `DispatcherServlet`, Jackson converters, error handlers, and an embedded Tomcat. Every annotation you used to write BY HAND in XML or Java config — Boot does for you, and backs off when you provide your own.

Three corollaries:

1. **Every `@RestController` method is reached through DispatcherServlet** — it's not a direct servlet. DispatcherServlet is the one servlet; your controllers are plain beans that it delegates to.
2. **`@RestController` = `@Controller` + `@ResponseBody`** — the `@ResponseBody` annotation tells Spring to serialize the return value via a `HttpMessageConverter` (Jackson → JSON) instead of resolving a view template.
3. **Auto-configuration is conditional** — `@ConditionalOnMissingBean`, `@ConditionalOnClass`. If you define your own `ObjectMapper` bean, Boot's auto-configured one backs off. This is why you can override any auto-configured behavior.

If you can verbalize those three points without notes, you have Chapter 3.

---

## 🪜 Concept build-up

---

### Part 1 — DispatcherServlet request lifecycle

When a `GET /api/orders/42` arrives:

```
  HTTP Request: GET /api/orders/42
       │
       ▼
  ┌─────────────────────────────────────────────────────┐
  │               DispatcherServlet                      │
  │                                                      │
  │  1. HandlerMapping.getHandler(request)               │
  │     → finds @GetMapping("/api/orders/{id}")          │
  │     → returns HandlerExecutionChain                  │
  │        (handler method + interceptors)               │
  │                                                      │
  │  2. HandlerInterceptor.preHandle()                   │
  │     → security checks, logging, etc.                 │
  │                                                      │
  │  3. HandlerAdapter.handle()                          │
  │     → invokes the @Controller method                 │
  │     → resolves @PathVariable, @RequestBody, etc.     │
  │     → gets the return value (e.g., Order object)     │
  │                                                      │
  │  4. HttpMessageConverter.write()                     │
  │     → Jackson serializes Order → JSON                │
  │     → writes to HttpServletResponse                  │
  │                                                      │
  │  5. HandlerInterceptor.afterCompletion()             │
  │     → cleanup, metrics, etc.                         │
  └─────────────────────────────────────────────────────┘
       │
       ▼
  HTTP Response: 200 OK {"id": 42, "status": "SHIPPED"}
```

> **What the JVM is actually doing:** `DispatcherServlet` extends `HttpServlet`. Tomcat calls `doDispatch()` (invoked from `service()`) on a request thread from the thread pool. `HandlerMapping` is a bean — typically `RequestMappingHandlerMapping` which scans all `@Controller` classes for `@RequestMapping`/`@GetMapping` annotations at startup and builds a lookup table of URL patterns → handler methods. Lookup is O(1) for exact matches, regex matching for path variables.

---

### Part 2 — Controllers and request mapping

```java
@RestController                    // @Controller + @ResponseBody
@RequestMapping("/api/orders")     // base path for all methods
public class OrderController {

    private final OrderService orderService;

    // Constructor injection — preferred (Chapter 2)
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // GET /api/orders/42
    @GetMapping("/{id}")
    public Order getOrder(@PathVariable Long id) {
        return orderService.findById(id)
            .orElseThrow(() -> new OrderNotFoundException(id));
    }

    // POST /api/orders with JSON body
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)   // 201 instead of default 200
    public Order createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.create(request);
    }

    // GET /api/orders?status=PENDING&page=0&size=20
    @GetMapping
    public Page<Order> listOrders(
        @RequestParam(defaultValue = "PENDING") String status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return orderService.findByStatus(status, PageRequest.of(page, size));
    }
}
```

**`@Controller` vs `@RestController`:**

| Annotation | What happens to the return value |
|---|---|
| `@Controller` | Interpreted as a **view name** — passed to a `ViewResolver` (Thymeleaf, JSP). If you want JSON, add `@ResponseBody` on each method. |
| `@RestController` | Interpreted as **response body** — serialized via `HttpMessageConverter` (Jackson → JSON). No view resolution. |

**`ResponseEntity<T>` — full control over the response:**

```java
@GetMapping("/{id}")
public ResponseEntity<Order> getOrder(@PathVariable Long id) {
    return orderService.findById(id)
        .map(order -> ResponseEntity.ok(order))
        .orElse(ResponseEntity.notFound().build());
    // Full control: status code, headers, body
}
```

---

### Part 3 — Exception handling with @ControllerAdvice

```java
@RestControllerAdvice    // applies to ALL controllers
public class GlobalExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(OrderNotFoundException e) {
        return new ErrorResponse("ORDER_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException e) {
        String details = e.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining(", "));
        return new ErrorResponse("VALIDATION_FAILED", details);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneral(Exception e) {
        log.error("Unexpected error", e);   // log full trace internally
        return new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred");
        // NEVER expose stack traces, class names, or SQL to external clients
    }
}

record ErrorResponse(String code, String message) {}
```

---

### Part 4 — Spring Boot: `@SpringBootApplication` decomposed

```java
@SpringBootApplication
public class OrderApp {
    public static void main(String[] args) {
        SpringApplication.run(OrderApp.class, args);
    }
}

// @SpringBootApplication is syntactic sugar for 3 annotations:
// 1. @Configuration     → this class is a source of bean definitions
// 2. @ComponentScan     → scan THIS package and all sub-packages for @Component/@Service/etc.
// 3. @EnableAutoConfiguration → scan classpath, find auto-configuration classes,
//                               register beans based on conditions
```

**How auto-configuration works (Spring Boot 3.x):**

```
  Boot scans: META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
       │
       ▼
  Finds auto-configuration class: WebMvcAutoConfiguration
       │
       ▼
  Checks conditions:
    @ConditionalOnClass(DispatcherServlet.class)  → YES, spring-webmvc is on classpath
    @ConditionalOnMissingBean(WebMvcConfigurer.class) → YES, you didn't define one
       │
       ▼
  Registers: DispatcherServlet, RequestMappingHandlerMapping,
             Jackson HttpMessageConverters, error handler, etc.

  If YOU define your own bean (e.g., custom ObjectMapper):
    @ConditionalOnMissingBean(ObjectMapper.class) → NO, you provided one → Boot backs off
```

**⚠️ Common mistake: `@SpringBootApplication` in a nested package:**

```java
// ❌ App class in com.walmart.orders.config
@SpringBootApplication
public class OrderApp { ... }
// @ComponentScan scans com.walmart.orders.config and below
// Beans in com.walmart.orders.service are MISSED — different package branch

// ✅ App class in com.walmart.orders (root package)
@SpringBootApplication
public class OrderApp { ... }
// Scans com.walmart.orders and ALL sub-packages — everything discovered
```

---

### Part 5 — Properties, profiles, and conditional beans

```yaml
# application.yml — default properties
server:
  port: 8080
app:
  cache:
    ttl: 300s
    max-size: 10000

# application-dev.yml — overrides for dev profile
server:
  port: 9090
app:
  cache:
    ttl: 10s
```

```java
// Type-safe property binding (preferred over @Value)
@ConfigurationProperties(prefix = "app.cache")
public record CacheProperties(Duration ttl, int maxSize) {}

// Enable it:
@Configuration
@EnableConfigurationProperties(CacheProperties.class)
public class CacheConfig {

    @Bean
    public CaffeineCacheManager cacheManager(CacheProperties props) {
        // props.ttl() and props.maxSize() are populated from YAML
        return new CaffeineCacheManager();
    }
}

// Profile-specific beans:
@Configuration
@Profile("dev")
public class DevConfig {
    @Bean
    public DataSource dataSource() {
        return new H2DataSource();   // in-memory DB for dev
    }
}

@Configuration
@Profile("prod")
public class ProdConfig {
    @Bean
    public DataSource dataSource() {
        return new HikariDataSource();   // real DB for prod
    }
}
```

**Property precedence (highest wins):**

```
  1. Command-line args: --server.port=9999
  2. OS environment variables: SERVER_PORT=9999
  3. Profile-specific YAML: application-prod.yml
  4. Default YAML: application.yml
  5. @PropertySource annotations
  6. SpringApplication defaults
```

---

## ❌/✅ Common mistakes

```java
// ❌ Mistake 1: Forgetting @ResponseBody (or using @Controller instead of @RestController)
@Controller   // NOT @RestController
public class OrderController {
    @GetMapping("/orders/{id}")
    public Order getOrder(@PathVariable Long id) {
        return orderService.findById(id);
    }
    // Spring tries to resolve "Order" as a VIEW NAME → 404 or template error
}
// ✅ Fix: use @RestController, or add @ResponseBody on the method

// ❌ Mistake 2: @Value without default for optional property
@Value("${app.feature.enabled}")   // crashes if property not set
private boolean featureEnabled;

// ✅ Fix: provide default
@Value("${app.feature.enabled:false}")
private boolean featureEnabled;

// ❌ Mistake 3: Two beans of same type without disambiguation
@Bean
public ObjectMapper objectMapper1() { return new ObjectMapper(); }
@Bean
public ObjectMapper objectMapper2() { return new ObjectMapper(); }
// NoUniqueBeanDefinitionException when autowiring ObjectMapper

// ✅ Fix: @Primary on the default, @Qualifier on the specific
@Bean @Primary
public ObjectMapper defaultMapper() { return new ObjectMapper(); }
@Bean @Qualifier("xml")
public ObjectMapper xmlMapper() { return new XmlMapper(); }
```

---

## 🎨 Visual — @SpringBootApplication decomposition

```
  @SpringBootApplication
  ─────────────────────────────────────────
  │
  ├── @Configuration
  │   "I am a source of @Bean definitions"
  │
  ├── @ComponentScan
  │   "Scan MY package + all sub-packages
  │    for @Component, @Service, @Repository,
  │    @Controller, @Configuration"
  │
  └── @EnableAutoConfiguration
      "Look at what's on the classpath.
       For each library found, register
       sensible default beans — BUT
       back off if the developer already
       defined their own."

       ┌─────────────────────────────────┐
       │ Classpath has spring-webmvc?    │
       │ → register DispatcherServlet    │
       │ → register HandlerMapping       │
       │ → register Jackson converters   │
       ├─────────────────────────────────┤
       │ Classpath has spring-data-jpa?  │
       │ → register EntityManagerFactory │
       │ → register TransactionManager   │
       ├─────────────────────────────────┤
       │ You defined your own DataSource?│
       │ → @ConditionalOnMissingBean     │
       │ → Boot's DataSource backs off   │
       └─────────────────────────────────┘

KEY INVARIANT:
   Auto-configuration is CONDITIONAL and POLITE.
   It provides defaults that back off when you override them.
   @ConditionalOnMissingBean is the mechanism.
```

---

## 🏢 Where you've seen this in your app

- Every `@RestController` in your Walmart services is reached through `DispatcherServlet` — even if you've never configured it directly. Boot did it.
- `application.yml` properties with `spring.profiles.active` switch between dev/stage/prod configs — different DB URLs, cache TTLs, feature flags.
- `@ControllerAdvice` centralizes error handling — your service returns structured error JSON instead of raw stack traces.
- If your service uses `@ConfigurationProperties`, the type-safe binding comes from Boot's auto-configuration of the property binder.

---

## 🎤 Interview Q&A

**Q1. What is `DispatcherServlet` and how does it route requests?**

> DispatcherServlet is a single servlet that Spring MVC registers. It intercepts every HTTP request. On each request, it asks a `HandlerMapping` (typically `RequestMappingHandlerMapping`) to find the controller method matching the URL and HTTP method. The mapping was built at startup by scanning all `@Controller` classes for `@RequestMapping`/`@GetMapping` annotations. Once the handler is found, DispatcherServlet invokes it via a `HandlerAdapter`, gets the return value, passes it through a `HttpMessageConverter` (Jackson for JSON), and writes the response. It's the front controller pattern — one entry point delegates to many handlers.

**Q2. What does `@SpringBootApplication` actually do?**

> It's a composed annotation combining three: `@Configuration` (this class can define `@Bean` methods), `@ComponentScan` (scan this package and sub-packages for Spring components), and `@EnableAutoConfiguration` (scan the classpath for libraries and auto-register sensible default beans). The auto-configuration uses `@ConditionalOnClass` (only if the library is present), `@ConditionalOnMissingBean` (only if the developer hasn't defined their own), and `@ConditionalOnProperty` (only if a property is set). This is why Boot is opinionated but overridable — every default has a condition that backs off when you provide your own bean.

**Q3. What is the difference between `@Controller` and `@RestController`?**

> `@RestController` = `@Controller` + `@ResponseBody`. With `@Controller`, the return value is treated as a view name — Spring looks for a template (Thymeleaf, JSP) to render. With `@RestController`, the return value is serialized directly into the response body via a `HttpMessageConverter` — Jackson turns it into JSON. If you use `@Controller` and forget `@ResponseBody`, your Order object is treated as a view name "Order" → 404 or template error. For REST APIs, always use `@RestController`.

**Q4. How do profiles work in Spring Boot?**

> Profiles let you swap configuration per environment. `application-dev.yml` overrides `application.yml` when `spring.profiles.active=dev`. You can also use `@Profile("dev")` on beans — that bean is only registered when the dev profile is active. Profile-specific properties override default properties but are themselves overridden by environment variables and command-line args. Common use: H2 in-memory DB for dev, HikariCP + PostgreSQL for prod — same code, different beans based on profile.

---

## 🧾 TL;DR

- **DispatcherServlet** = one servlet that routes all requests via HandlerMapping → Controller → MessageConverter → Response.
- **`@RestController`** = `@Controller` + `@ResponseBody`. Return values → JSON via Jackson. No view resolution.
- **`@SpringBootApplication`** = `@Configuration` + `@ComponentScan` + `@EnableAutoConfiguration`.
- **Auto-config** is conditional: `@ConditionalOnMissingBean` backs off when you define your own bean.
- **Properties precedence:** command-line > env vars > profile YAML > default YAML.
- **`@ConfigurationProperties`** = type-safe property binding (preferred over `@Value`).
- **`@ControllerAdvice`** = centralized exception handling for all controllers.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Sep 2026 | Chapter 3 created as Note #12 (Phase 3) of the JavaBackend KB completion roadmap. Merges Spring plan Days 6-8: DispatcherServlet lifecycle, HandlerMapping, @Controller vs @RestController, ResponseEntity, @ControllerAdvice, @SpringBootApplication decomposition, auto-configuration mechanism (@ConditionalOnMissingBean), properties/profiles/precedence, @ConfigurationProperties, @Profile beans. Follows Spring 8-section arc format. |
