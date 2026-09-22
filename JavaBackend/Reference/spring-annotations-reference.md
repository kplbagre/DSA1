# ⚡ Spring Annotations — Quick Reference

> **Use:** alphabetical lookup of Spring annotations with one-line semantics, the gotcha, and which chapter covers it in depth.

---

## 🔹 Stereotype / Component Annotations

| Annotation | What it does | Gotcha | Depth |
|---|---|---|---|
| `@Component` | Marks a class for component scanning → registered as a bean | Scanning starts from `@SpringBootApplication` package downward | Ch 2 |
| `@Service` | `@Component` alias. Semantic: business logic layer | No functional difference from @Component | Ch 2 |
| `@Repository` | `@Component` alias. Semantic: data access. Adds exception translation (SQL → DataAccessException) | Exception translation only works with Spring-managed persistence | Ch 2/4 |
| `@Controller` | `@Component` alias. Handles web requests. Return value = view name | Forgetting @ResponseBody → Spring resolves return as view | Ch 3 |
| `@RestController` | `@Controller` + `@ResponseBody`. Return value = JSON body | Default for REST APIs | Ch 3 |
| `@Configuration` | Marks a class as a source of `@Bean` definitions. CGLIB-proxied: `@Bean` methods are intercepted to enforce singleton semantics | Calling one @Bean method from another returns the SAME instance (proxy intercepts) | Ch 2 |

---

## 🔹 Dependency Injection

| Annotation | What it does | Gotcha | Depth |
|---|---|---|---|
| `@Autowired` | Inject a dependency (constructor, field, or setter) | Field injection hides dependencies + breaks tests. Prefer constructor. | Ch 2 |
| `@Qualifier("name")` | Disambiguate when multiple beans of the same type exist | Required with `@Autowired` when > 1 candidate bean | Ch 2/3 |
| `@Primary` | Marks a bean as the default when multiple candidates exist | Overridden by `@Qualifier` at injection point | Ch 3 |
| `@Value("${prop}")` | Inject a property value from application.yml | Missing property → startup crash. Use default: `${prop:default}` | Ch 3 |
| `@ConfigurationProperties(prefix)` | Type-safe property binding to a POJO/record | Must enable with `@EnableConfigurationProperties` or `@ConfigurationPropertiesScan` | Ch 3 |
| `@Lazy` | Delay bean creation until first use | Hides startup errors — bean fails on first request, not at startup | Ch 2 |

---

## 🔹 Bean Lifecycle

| Annotation | What it does | Gotcha | Depth |
|---|---|---|---|
| `@PostConstruct` | Run method after dependency injection completes | Runs AFTER `@Autowired` fields are set | Ch 2 |
| `@PreDestroy` | Run method before bean is destroyed | NOT called for prototype-scoped beans | Ch 2 |
| `@Bean` | Declares a bean inside a `@Configuration` class | Method name = bean name by default | Ch 2/3 |
| `@Scope("prototype")` | New instance per injection (not singleton) | Prototype injected into singleton = effectively singleton | Ch 2 |
| `@Profile("dev")` | Bean registered only when profile is active | Multiple profiles: `@Profile({"dev", "test"})` — OR logic | Ch 3 |

---

## 🔹 Web / MVC

| Annotation | What it does | Gotcha | Depth |
|---|---|---|---|
| `@RequestMapping(path, method)` | Map URL + HTTP method to handler | `@GetMapping`, `@PostMapping` etc. are shortcuts | Ch 3 |
| `@GetMapping("/path")` | Shortcut for `@RequestMapping(method = GET)` | | Ch 3 |
| `@PostMapping` | Shortcut for POST | | Ch 3 |
| `@PathVariable` | Bind URL segment to parameter: `/orders/{id}` | Name must match: `@PathVariable Long id` for `/{id}` | Ch 3 |
| `@RequestParam` | Bind query param: `?status=PENDING` | Required by default. Optional: `@RequestParam(required = false)` | Ch 3 |
| `@RequestBody` | Deserialize JSON request body to object | Must have Jackson on classpath. Content-Type must be application/json | Ch 3 |
| `@ResponseBody` | Serialize return value to JSON response body | Implicit in `@RestController` | Ch 3 |
| `@ResponseStatus(HttpStatus.CREATED)` | Set HTTP status code on the response | Overridden if you return ResponseEntity with a different status | Ch 3 |
| `@ControllerAdvice` | Global exception handler for all controllers | `@RestControllerAdvice` = `@ControllerAdvice` + `@ResponseBody` | Ch 3 |
| `@ExceptionHandler(Ex.class)` | Handle specific exception type in controller or @ControllerAdvice | Most specific exception type matched first | Ch 3 |
| `@Valid` | Trigger bean validation on @RequestBody | Requires `spring-boot-starter-validation` | Ch 3 |

---

## 🔹 Transaction / Data

| Annotation | What it does | Gotcha | Depth |
|---|---|---|---|
| `@Transactional` | Wrap method in DB transaction (via AOP proxy) | Self-call bypasses proxy. Private method = silent no-op. Checked exceptions don't rollback by default. | Ch 2/4 |
| `@Transactional(readOnly = true)` | Optimization: skip dirty checking + flush | Does NOT prevent writes — just an optimization hint | Ch 4 |
| `@Transactional(rollbackFor = X.class)` | Roll back on specific checked exceptions | Without this, checked exceptions COMMIT (not rollback) | Ch 4 |
| `@Transactional(propagation = REQUIRES_NEW)` | Always start a new independent transaction | Suspends the existing transaction — commits independently | Ch 4 |
| `@Entity` | JPA entity — maps to a database table | Must have no-arg constructor (Hibernate requirement) | Ch 4 |
| `@Id` | Primary key field | Usually paired with `@GeneratedValue` | Ch 4 |
| `@OneToMany(fetch = LAZY)` | Lazy-loaded collection. LAZY is default for collections | Accessing outside @Transactional → LazyInitializationException | Ch 4 |
| `@ManyToOne(fetch = LAZY)` | ⚠️ EAGER is default for @ManyToOne — always set LAZY explicitly | Hidden N+1 source if left as EAGER | Ch 4 |
| `@Query("JPQL")` | Custom JPQL query on repository method | Use `nativeQuery = true` for raw SQL | Ch 4 |
| `@EntityGraph(attributePaths)` | Declarative eager loading for specific query | Alternative to JOIN FETCH in @Query | Ch 4 |

---

## 🔹 Security

| Annotation | What it does | Gotcha | Depth |
|---|---|---|---|
| `@EnableWebSecurity` | Enable Spring Security configuration | Required on the config class | Ch 5 |
| `@PreAuthorize("hasRole('ADMIN')")` | Method-level authorization check | Requires `@EnableMethodSecurity` on config class | Ch 5 |
| `@Secured("ROLE_ADMIN")` | Simpler method-level role check | Less flexible than @PreAuthorize (no SpEL) | Ch 5 |

---

## 🔹 Async / Scheduling

| Annotation | What it does | Gotcha | Depth |
|---|---|---|---|
| `@Async` | Execute method on a separate thread | Self-call bypasses proxy (same trap as @Transactional). Return `CompletableFuture` for results. | — |
| `@EnableAsync` | Enable @Async processing | Must be on a @Configuration class | — |
| `@Scheduled(fixedRate = 5000)` | Run method every 5 seconds | If method throws → all future executions silently cancelled. Always try-catch. | — |
| `@EnableScheduling` | Enable @Scheduled processing | Must be on a @Configuration class | — |

---

## 🔹 Spring Boot

| Annotation | What it does | Gotcha | Depth |
|---|---|---|---|
| `@SpringBootApplication` | `@Configuration` + `@ComponentScan` + `@EnableAutoConfiguration` | Must be in the ROOT package — @ComponentScan scans downward | Ch 3 |
| `@ConditionalOnMissingBean` | Auto-config: register bean ONLY if user didn't define one | How Boot "backs off" — the politeness mechanism | Ch 3 |
| `@ConditionalOnClass` | Auto-config: register bean ONLY if class is on classpath | Starter JARs use this to conditionally configure | Ch 3 |
| `@ConditionalOnProperty` | Auto-config: register bean ONLY if property is set | `prefix + name + havingValue` | Ch 3 |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Sep 2026 | Note #35 (Phase 5). All major Spring annotations grouped by concern, with one-line semantics, gotcha, and cross-reference to Spring track chapter. |
