# System Qualities — The 7 DocuSign Evaluation Dimensions

> **Format note:** This is a survey/reference note, not a single-concept deep-dive. Instead of one mental model + one implementation, each section covers all 7 dimensions in parallel. Format adapted from `notes-standards.md` to serve the survey purpose; 20-minute revision budget still applies.

---

## 🎯 Why This Matters

The DocuSign interview PDF does not ask "can you design X?" in isolation. It grades your design against **seven named dimensions**: Testability, Usability, Extensibility, Security, Availability, Scalability, and Observability & Traceability.

A candidate who designs a correct rate limiter but says nothing about observability scores lower than a candidate who also explains "I'd emit a `rate_limit_exceeded` metric with `tenant_id` and `endpoint` tags and trace every rejected request with a correlation ID." This note teaches you to name the dimension, state how your design addresses it, and drop the right technical signal — for every one of the seven.

**Interview round:** Every system design round — Type A (System Design) and Type B (Product Architecture) both.
**Senior signal:** Junior candidates describe mechanics. Senior candidates state which quality goal each decision serves.

---

## 🧠 The Mental Models — One Analogy Per Dimension

Each analogy is designed to be retold without any technical vocabulary.

---

### 🧠 Testability — The Dishwasher With a Test Port

Imagine a dishwasher factory that tests every machine before shipping. They don't fill it with real dirty dishes and real detergent every time. Instead, they connect a diagnostic kit to a test port on the side — a standardised plug that lets them inject simulated water pressure, fake temperature readings, and mock sensors. The machine runs its program against the test inputs and they verify it works correctly on a bench, before it ever reaches a kitchen.

Software testability works the same way. A class that creates its own dependencies (database, email sender, clock) is like a dishwasher with no test port — you can only test it inside a running kitchen. But a class that **accepts** its dependencies from the outside (through constructor injection) has a test port: in tests, you plug in a fake database, a fake clock, and a fake email sender without touching the production code at all.

**The key insight is:** a component is testable if and only if you can swap all its dependencies for test doubles without modifying the component itself. The way you create that swap point is the interface.

---

### 🧠 Usability — The TV Remote Rule

The TV remote rule: the viewer should be able to change the channel by pressing one button on the front — they should never need to open the back panel or know anything about how the TV's tuner works internally.

For an API, usability means: a developer calling your endpoint should be able to use it correctly from the documentation alone, without asking you what the right HTTP verb is, what happens on a duplicate call, or how to interpret a 422 error. An unusable API is one where callers constantly have to ask "wait, does a 400 mean validation failed or rate limited?" or "does this endpoint create a new resource or replace an existing one?"

**The key insight is:** usability is about *predictability* — every naming choice, every status code, every error message should behave exactly as a reasonable engineer would expect before they read the docs.

---

### 🧠 Extensibility — The Power Strip

A power strip doesn't care what you plug into it. You can plug in a laptop, a phone charger, a lamp, or a device that didn't exist when the strip was manufactured. The strip has a fixed contract (two or three pins, standard voltage) and everything that honours that contract works — no rewiring, no modifications to the strip itself.

Extensible software works the same way. An interface is the socket. Every class that implements the interface is a new device plugging in. The core logic (the power strip) never changes; the behaviour expands by adding new implementations.

The moment you add a new feature by editing existing code (adding `if (type.equals("SMS"))` to a method that already has `if (type.equals("EMAIL"))`), you've rewired the wall. That's the sign the design is not extensible.

**The key insight is:** extensibility is the Open-Closed Principle — define the contract as an interface, close the core for modification, and open it for extension by adding new implementations that plug in.

---

### 🧠 Security — The Airport

An airport has five independent security layers. The perimeter fence stops vehicles. The check-in desk verifies your booking exists (identity check). The passport control gate confirms who you are (authentication). The boarding gate confirms you're allowed on *this particular flight* (authorisation). The bag scanner ensures nothing dangerous is carried through (payload validation). And your luggage is locked (data at rest is encrypted).

If one layer fails — say the passport scanner is down — the other four still stop threats. No single point bypasses everything. This is defence in depth.

In distributed systems: the firewall is the perimeter, the authentication service checks identity, the authorisation layer checks permissions per resource, input validation rejects malformed payloads, and encryption at rest + in transit protects data even if storage or network is compromised.

**The key insight is:** security is not a single gate — it is layers where each layer assumes the previous one might fail.

---

### 🧠 Availability — The Hospital Power Supply

A hospital operating theatre has three independent power sources: the city grid, a diesel generator, and a UPS (uninterruptible power supply — a battery bank). If the city grid fails, the generator starts within 10 seconds. If the generator fails mid-surgery, the UPS battery powers everything for 30 minutes. No single failure turns the lights off.

Software availability works the same way. An availability target (SLO) of 99.9% allows only 8.7 hours of downtime per year. Achieving it requires redundancy at every layer — multiple instances behind a load balancer, multiple database replicas, circuit breakers that stop cascading failures from spreading, and graceful degradation when a dependency is slow.

**The key insight is:** availability is not achieved by making components more reliable in isolation — it is achieved by designing so that any single failure is transparently absorbed by redundant components.

---

### 🧠 Scalability — The Highway Toll Booth Problem

Adding more highway lanes moves more cars. But if all lanes funnel to a single toll booth at the exit, the toll booth becomes the bottleneck and all the extra lanes are wasted. The correct fix is to identify and scale the bottleneck — not to add more of whatever is easiest to add.

In distributed systems: for a read-heavy service (90% reads), the bottleneck is the database read path. Adding more application servers doesn't help if they all hammer the same single database. The fix is read replicas + an L2 cache in front of the DB. For a write-heavy service, the bottleneck is the DB write path — fixed with sharding or by offloading writes to an async queue.

**The key insight is:** scalability is not about making everything bigger — it is about finding the single toll booth and addressing it specifically.

---

### 🧠 Observability & Traceability — The Flight Recorder + Air Traffic Control Radar

A flight recorder (black box) records every sensor reading, every control input, and every system event during the entire flight. After a crash, investigators can reconstruct exactly what happened, in what order, and why. The air traffic control radar shows every aircraft's real-time position, altitude, and heading — so controllers can catch problems before they become crashes.

Together: you can investigate any historical incident (black box) AND catch live anomalies before they become incidents (radar).

Observability is the software equivalent. Three pillars: **Logs** are the black box — timestamped events you can search and replay. **Metrics** are the radar — numbers that trend over time (P99 latency, error rate, queue depth) that you watch on a dashboard. **Traces** are the flight path — the complete journey of one request as it crosses from service A to service B to service C, with each hop as a labelled segment.

**The key insight is:** observability is not just logging more — it is making every request reconstructable across all services by sharing a common trace_id that appears in logs, metrics, and traces simultaneously.

---

## 🎨 Visual — Where Each Dimension Applies in a Request Lifecycle

```
INCOMING REQUEST
       ↓
┌─────────────────────────────────────────┐
│  Load Balancer / API Gateway            │
│                                         │
│  → AVAILABILITY: routes traffic around  │
│    failed instances; health checks      │
└─────────────────────────────────────────┘
       ↓
┌─────────────────────────────────────────┐
│  Auth + Input Validation Layer          │
│                                         │
│  → SECURITY: JWT validation (authn),    │
│    @PreAuthorize (authz),               │
│    payload validation (400 on bad input)│
│  → USABILITY: standard error body       │
│    returned on every failure            │
└─────────────────────────────────────────┘
       ↓
┌─────────────────────────────────────────┐
│  Service Layer (Business Logic)         │
│                                         │
│  → EXTENSIBILITY: Strategy interface,   │
│    new behaviours plug in               │
│  → TESTABILITY: constructor-injected    │
│    dependencies, swappable for mocks    │
└─────────────────────────────────────────┘
       ↓
┌─────────────────────────────────────────┐
│  Data Layer (DB + Cache)                │
│                                         │
│  → SCALABILITY: read replicas + cache   │
│    for read-heavy; sharding for         │
│    write-heavy bottleneck               │
└─────────────────────────────────────────┘

╔═════════════════════════════════════════╗
║  Observability — Cross-Cutting Concern  ║
║                                         ║
║  → OBSERVABILITY & TRACEABILITY:        ║
║    trace_id in MDC propagated through   ║
║    all layers; logs + metrics + traces  ║
║    share the same trace_id              ║
╚═════════════════════════════════════════╝

KEY INVARIANT:
   Six dimensions are scoped to a layer; Observability is the one
   that cuts across ALL layers — it is the connective tissue that
   lets you reconstruct what happened at every layer post-incident.
```

---

## ⚙️ How It Actually Works — Implementation Pattern Per Dimension

---

### Testability — Constructor Injection + Interface Contract

**Steps:**
1. **Define a dependency as an interface** — not a concrete class. The interface is the contract; it's what both production and test code agree on.
2. **Inject the dependency through the constructor** — Spring wires in the real implementation at runtime; your test wires in a mock.
3. **Test the service against a mock** — the mock's behaviour is controlled by the test, so every scenario is reproducible without a live database or HTTP call.

```java
// Step 1 — dependency defined as an interface, not a concrete class
public interface DocumentRepository {
    Document findById(String documentId);
    void save(Document document);
}

// Step 2 — service accepts the interface through constructor injection
@Service
public class SigningService {

    private final DocumentRepository documentRepository;
    private final AuditLogger auditLogger;

    // Spring injects the real implementations at runtime
    public SigningService(DocumentRepository documentRepository,
                          AuditLogger auditLogger) {
        this.documentRepository = documentRepository;
        this.auditLogger = auditLogger;
    }

    public void sign(String documentId, String signerId) {
        Document doc = documentRepository.findById(documentId);
        doc.addSignature(signerId);
        documentRepository.save(doc);
        auditLogger.log(documentId, signerId, "SIGNED");
    }
}

// Step 3 — test swaps in a mock without touching production code
@SpringBootTest
class SigningServiceTest {

    @MockBean
    private DocumentRepository documentRepository;

    @MockBean
    private AuditLogger auditLogger;

    @Autowired
    private SigningService signingService;

    @Test
    void signAddsSignatureToDocument() {
        Document doc = new Document("doc-1");
        when(documentRepository.findById("doc-1")).thenReturn(doc);

        signingService.sign("doc-1", "signer-42");

        verify(documentRepository).save(argThat(d -> d.hasSignature("signer-42")));
    }
}
```

### What is `@MockBean`, and why does it fit here?

`@MockBean` (from Spring Boot Test) replaces a real Spring bean with a Mockito mock in the application context. In an interview, if asked: "It lets me test a service in isolation — I inject a fake repository that returns controlled test data, so I don't need a live database to run the test."

---

### Usability — Standard Error Format + HTTP Verb Semantics

**Steps:**
1. **Use HTTP verbs by their contract** — GET is idempotent + read-only, POST creates, PUT replaces (idempotent), PATCH updates a subset, DELETE removes.
2. **Return a standard error body** on every 4xx and 5xx — same structure every time, machine-readable error code, human-readable message, and a correlationId for support lookups.
3. **Use the right status codes** — 200 OK, 201 Created, 204 No Content, 400 Bad Request (validation), 401 Unauthorized, 403 Forbidden, 404 Not Found, 409 Conflict, 422 Unprocessable Entity (semantic validation failure), 429 Too Many Requests.

```java
// Step 2 — standard error body returned on every failure
public class ApiError {

    private final String error;         // machine-readable code: "DOCUMENT_NOT_FOUND"
    private final String message;       // human-readable: "Document doc-1 was not found"
    private final String correlationId; // trace_id for support lookup
    private final Instant timestamp;

    // constructor, getters omitted for brevity — all fields are set at construction
    public ApiError(String error, String message, String correlationId) {
        this.error = error;
        this.message = message;
        this.correlationId = correlationId;
        this.timestamp = Instant.now();
    }
}

// Step 3 — @ControllerAdvice maps every exception to the right status code
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(DocumentNotFoundException ex) {
        String correlationId = MDC.get("traceId"); // links error to the trace
        ApiError error = new ApiError("DOCUMENT_NOT_FOUND", ex.getMessage(), correlationId);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleValidation(ConstraintViolationException ex) {
        String correlationId = MDC.get("traceId");
        ApiError error = new ApiError("VALIDATION_FAILED", ex.getMessage(), correlationId);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
}
```

---

### Extensibility — Strategy Pattern + Open-Closed Principle

**Steps:**
1. **Define the extension point as an interface** — the "socket" that new behaviours plug into.
2. **Implement each behaviour as a separate class** — each class does one thing, fulfils the interface contract.
3. **Select the implementation at runtime** — via a registry map or Spring's `@Qualifier`, with no `if-else` chain in the caller.

```java
// Step 1 — the extension point: adding a new channel = adding a new implementation
public interface NotificationStrategy {
    void send(Notification notification);
    NotificationChannel channel();
}

// Step 2 — each channel is a self-contained class; adding WhatsApp doesn't touch Email
@Component
public class EmailNotificationStrategy implements NotificationStrategy {
    @Override
    public void send(Notification notification) {
        // send via email SMTP
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }
}

@Component
public class SmsNotificationStrategy implements NotificationStrategy {
    @Override
    public void send(Notification notification) {
        // send via SMS gateway
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.SMS;
    }
}

// Step 3 — registry built from all implementations; no if-else, no modification needed
@Service
public class NotificationService {

    private final Map<NotificationChannel, NotificationStrategy> strategies;

    // Spring injects all NotificationStrategy beans as a list
    public NotificationService(List<NotificationStrategy> strategyList) {
        this.strategies = strategyList.stream()
            .collect(Collectors.toMap(NotificationStrategy::channel, s -> s));
    }

    public void notify(Notification notification) {
        NotificationStrategy strategy = strategies.get(notification.getChannel());
        if (strategy == null) {
            throw new UnsupportedChannelException(notification.getChannel());
        }
        strategy.send(notification);
    }
}
```

> **Why this is Open-Closed:** adding `WhatsAppNotificationStrategy` requires zero changes to `NotificationService`. The class is closed for modification but open for extension.

---

### Security — Defence in Depth (Authentication → Authorisation → Encryption)

**Steps:**
1. **Authenticate at the boundary** — validate the JWT on every inbound request in a filter before the request reaches business logic.
2. **Authorise at the resource level** — after identity is confirmed, check that this identity is allowed to perform this specific action on this specific resource.
3. **Encrypt at rest and in transit** — TLS for network transport, AES-256 for stored data; never log sensitive values.

```java
// Step 1 — JWT validation filter: runs before every controller method
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenValidator tokenValidator;

    public JwtAuthenticationFilter(JwtTokenValidator tokenValidator) {
        this.tokenValidator = tokenValidator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return;
        }

        String token = authHeader.substring(7);
        Claims claims = tokenValidator.validate(token); // throws on invalid/expired token
        SecurityContextHolder.getContext()
            .setAuthentication(new JwtAuthenticationToken(claims));

        filterChain.doFilter(request, response);
    }
}

// Step 2 — resource-level authorisation: confirm this signer owns this document
@RestController
@RequestMapping("/v1/documents")
public class DocumentController {

    @PostMapping("/{documentId}/sign")
    @PreAuthorize("hasRole('SIGNER')")
    public ResponseEntity<Void> sign(@PathVariable String documentId,
                                     @AuthenticationPrincipal JwtAuthenticationToken auth) {
        String signerId = auth.getClaim("sub");
        // service layer confirms signerId is on the document's allowed signers list
        signingService.sign(documentId, signerId);
        return ResponseEntity.noContent().build();
    }
}
```

### What is `@PreAuthorize`, and why does it fit here?

`@PreAuthorize` is a Spring Security annotation that evaluates a SpEL (Spring Expression Language — a mini expression syntax Spring evaluates at runtime) expression before the method runs, rejecting the request with 403 if the expression is false. In an interview, if asked: "It enforces authorisation at the method level — I use it so that even if someone calls the method directly (bypassing the controller), the permission check still runs."

---

### Availability — SLI/SLO/SLA + Circuit Breaker

**The three availability terms defined clearly:**

| Term | Definition | Example |
|---|---|---|
| **SLI** (Service Level Indicator) | The metric you actually measure | "99.2% of requests in the past 30 days returned 2xx within 500ms" |
| **SLO** (Service Level Objective) | Your internal target | "We target 99.9% availability" |
| **SLA** (Service Level Agreement) | The contractual promise to customers | "We guarantee 99.5% — lower than the SLO so engineers have an error budget buffer" |

**Why SLA < SLO:** the gap between 99.9% (SLO) and 99.5% (SLA) is the **error budget** — time for planned deployments, rare incidents, and investigation without breaching the customer contract.

**99.9% availability = 8.76 hours of allowed downtime per year** (1 − 0.999) × 365 × 24 = 8.76 hrs.

**Steps for circuit breaker (prevents cascading failure):**
1. **Closed state (normal):** requests pass through; failure count is tracked.
2. **Open state (tripped):** when failures exceed the threshold, the circuit opens and all requests immediately fail fast with a cached/fallback response — no waiting for timeouts.
3. **Half-open state (probing):** after a wait window, one request is allowed through; if it succeeds the circuit closes; if it fails the circuit reopens.

```java
// Circuit breaker using Resilience4j — prevents a slow downstream service
// from cascading and making your service slow too
@Service
public class EnvelopeStatusService {

    private final CircuitBreaker circuitBreaker;
    private final ExternalStatusClient statusClient;

    public EnvelopeStatusService(CircuitBreakerRegistry registry,
                                  ExternalStatusClient statusClient) {
        // Configured: open after 50% failures in a 10-call sliding window
        this.circuitBreaker = registry.circuitBreaker("externalStatus");
        this.statusClient = statusClient;
    }

    public EnvelopeStatus getStatus(String envelopeId) {
        Supplier<EnvelopeStatus> decoratedCall =
            CircuitBreaker.decorateSupplier(circuitBreaker,
                () -> statusClient.fetchStatus(envelopeId));

        // Fallback: return UNKNOWN status rather than timing out the caller
        return Try.ofSupplier(decoratedCall)
            .recover(CallNotPermittedException.class, ex -> EnvelopeStatus.UNKNOWN)
            .get();
    }
}
```

### What is Resilience4j (circuit breaker library), and why does it fit here?

Resilience4j (a fault-tolerance Java library built for functional programming) is the standard Java library for circuit breakers, retry, rate limiting, and bulkhead patterns. In an interview, if asked: "Resilience4j wraps a function call and tracks failures in a sliding window — when failures exceed the threshold, it opens the circuit and returns an immediate fallback so the caller never waits for a timeout."

---

### Scalability — Identify the Bottleneck First

**Rule before drawing anything:** state which path is the bottleneck (read path or write path), then name the targeted fix.

**Read-heavy bottleneck (e.g., document retrieval: 90% reads):**
- Add **read replicas** — primary handles writes and replicates asynchronously to 2-3 replicas; read traffic is distributed across replicas
- Add an **L2 cache** (Redis) in front of the DB — hot documents served from memory, DB hit only on cache miss

**Write-heavy bottleneck (e.g., audit event ingestion: high write throughput):**
- **Async queue** — API acknowledges immediately; a worker drains the queue and writes to DB at a sustainable rate
- **Sharding** — distribute write load across multiple DB nodes by a shard key (e.g., `tenant_id`)

```java
// Read-heavy pattern: cache-aside on document retrieval
@Service
public class DocumentReadService {

    private final RedisTemplate<String, Document> redisTemplate;
    private final DocumentRepository documentRepository;

    private static final Duration CACHE_TTL = Duration.ofMinutes(15);

    public DocumentReadService(RedisTemplate<String, Document> redisTemplate,
                                DocumentRepository documentRepository) {
        this.redisTemplate = redisTemplate;
        this.documentRepository = documentRepository;
    }

    public Document getDocument(String documentId) {
        String cacheKey = "doc:" + documentId;

        // Check cache first — serve from memory, not DB
        Document cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // Cache miss — load from DB read replica, then populate cache
        Document doc = documentRepository.findById(documentId)
            .orElseThrow(() -> new DocumentNotFoundException(documentId));

        redisTemplate.opsForValue().set(cacheKey, doc, CACHE_TTL);
        return doc;
    }
}
```

> **Cross-reference:** `03-caching.md` covers cache eviction policies, stampede protection, and write strategies in full depth.

---

### Observability & Traceability — Logs + Metrics + Traces

**Steps:**
1. **Assign a trace_id at the entry point** — every inbound request gets a UUID; store it in MDC (Mapped Diagnostic Context — a thread-local key-value store that automatically appends fields like `trace_id` to every log line written from that thread).
2. **Propagate the trace_id** across service calls — pass it as an HTTP header (`X-Trace-Id`) so downstream services log under the same ID.
3. **Emit structured logs** — JSON format so logs are machine-searchable; every log line carries `trace_id`, `tenant_id`, `endpoint`, `duration_ms`.
4. **Emit metrics** — counter for requests (total, by status), histogram for latency (P50/P99), gauge for queue depth.

```java
// Step 1 — assign trace_id at request entry and store in MDC
@Component
public class TracingFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        // Honour incoming trace_id (from a calling service) or create a new one
        String traceId = Optional.ofNullable(request.getHeader(TRACE_ID_HEADER))
            .orElse(UUID.randomUUID().toString());

        // MDC makes traceId available to ALL log statements in this thread automatically
        MDC.put("traceId", traceId);
        response.setHeader(TRACE_ID_HEADER, traceId); // return it so the caller can log it too

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear(); // always clear — thread pool reuse otherwise leaks state
        }
    }
}

// Step 4 — metrics: Micrometer counter + timer wired to every signing operation
@Service
public class SigningMetrics {

    private final Counter signingAttempts;
    private final Timer signingLatency;

    public SigningMetrics(MeterRegistry meterRegistry) {
        this.signingAttempts = Counter.builder("signing.attempts")
            .description("Total signing attempts")
            .register(meterRegistry);

        this.signingLatency = Timer.builder("signing.latency")
            .description("Signing operation latency")
            .publishPercentiles(0.5, 0.99) // P50 and P99
            .register(meterRegistry);
    }

    public void recordAttempt() {
        signingAttempts.increment();
    }

    public <T> T recordLatency(Supplier<T> operation) {
        return signingLatency.record(operation);
    }
}
```

### What is MDC (Mapped Diagnostic Context), and why does it fit here?

MDC is a thread-local key-value map provided by SLF4J (a logging facade — a common API that sits in front of the actual logger like Logback or Log4j) that automatically appends named fields to every log statement on that thread. In an interview, if asked: "MDC is how I make trace_id appear in every log line without passing it explicitly to every method — I set it once in the filter and every `log.info()` call in the entire request includes it automatically."

### What is Micrometer, and why does it fit here?

Micrometer (a vendor-neutral application metrics instrumentation library for Java) lets you record counters, timers, gauges, and histograms using a consistent API, and then export the numbers to whatever backend your team uses (Prometheus, Datadog, CloudWatch). In an interview, if asked: "Micrometer is to metrics what SLF4J is to logging — it's the instrumentation API; the backend (Prometheus, Datadog) is pluggable without changing the instrumentation code."

---

## 🏢 Real World — How Companies Address Each Dimension

- **Stripe (Testability):** their entire payment library is built around interfaces (`PaymentProcessor`, `FraudScorer`) that can be swapped for test doubles — their engineering blog explicitly cites this as how they maintain 100% unit test coverage on payment-critical paths.

- **Twilio (Usability):** their REST API is cited in nearly every "good API design" list — consistent error structure with a `code` field (machine-readable) and `message` (human-readable), stable versioning (`/v1/` in path), and idempotent message sends via an `IdempotencyKey` header.

- **Netflix (Extensibility):** their recommendation system uses a Strategy-like plugin model — each new recommendation algorithm (trending, similar content, personal history) is a pluggable module; adding a new algorithm doesn't touch the core recommendation service.

- **DocuSign (Security):** every signature action is cryptographically tied to an authenticated identity using PKI (Public Key Infrastructure — the system of certificates, private keys, and certificate authorities used to verify digital identities). Non-repudiation (the legal guarantee that a signer cannot later deny signing) depends entirely on the security layer being correct.

- **Amazon (Availability):** their "cell-based architecture" ensures that a failure in one availability zone (an isolated data centre) doesn't cascade to others — each cell is fully independent, active, and handles a fraction of global traffic.

- **Uber (Scalability):** during New Year's Eve, ride requests spike 5-10× normal. Uber pre-scales the matching service specifically (the write-heavy bottleneck), not the GPS ingestion or payment services — they scale the toll booth, not the highway lanes.

- **Google (Observability):** Dapper (Google's internal distributed tracing system — the inspiration for OpenTelemetry) was built because debugging a request that touched 50+ microservices was impossible without a trace showing the exact path and latency of each hop.

---

## 🧭 When to Emphasise Which Dimension

In a DocuSign interview, the dimension you should lead with depends on the question type:

| Question type | Lead dimension | Why |
|---|---|---|
| Rate Limiter (C1 — Type A) | Scalability + Availability | It's built to handle high traffic; it must not be a single point of failure |
| Pagination API (C3 — Type B) | Usability + Extensibility | The API contract IS the product; it must be intuitive and forward-compatible |
| Expense Report (C2 — Type B) | Testability + Extensibility | Complex business rules must be unit-testable; approval workflows must be pluggable |
| Digital Signature (D1 — Mixed) | Security + Observability | Every signing action is a legal event; it must be cryptographically sound and fully auditable |
| Notification Service (D3 — Type A) | Extensibility + Availability | New channels must plug in; notifications must be delivered even during partial failures |
| Document Storage (D2 — Type B) | Availability + Security | Documents must be durable and tamper-evident; deletion must be controlled |

**The common mistake:** answering every dimension equally. A senior candidate prioritises — "for this specific question, Availability and Security are the primary grading dimensions; I'll cover the others briefly."

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | A vocabulary for grading your own design before the interviewer does. You can say "this design trades Availability against Consistency by choosing eventual replication" — which is exactly the level of thinking DocuSign says it looks for. |
| **You lose** | Speed. Covering all 7 dimensions deliberately adds 5-8 minutes to a 60-minute answer. You must prioritise — cover the 2-3 most relevant dimensions deeply and name the others briefly. |
| **Failure mode** | Treating dimensions as a checkbox list. "My system is observable because I have logging" fails the interview. "My system emits structured logs with trace_id, a P99 latency metric per endpoint, and distributed traces through all 3 services" passes it. |

---

## 🔬 Interview Q&As

### Q: "What does testability mean in a system design context — and how do you achieve it?"
> Testability means every component can be verified in isolation without starting the full system. The mechanism is constructor injection through interfaces: instead of creating a `new DatabaseRepository()` inside the service, the service accepts a `DocumentRepository` interface parameter. In tests, you inject a mock; in production, Spring injects the real implementation. No code change, no integration harness needed.

### Q: "What makes an API usable or unusable?"
> A usable API behaves predictably — the right HTTP verb (GET never modifies state, POST creates, PUT replaces), consistent error format (same `{ "error": "CODE", "message": "...", "correlationId": "..." }` on every 4xx and 5xx), and correct status codes (422 for semantic validation failures, not 400). An unusable API is one where callers have to ask you whether to retry a 503, or what the difference between your 400 and 422 is.

### Q: "What is the Open-Closed Principle, and where would you apply it in a notification service?"
> Open-Closed means a class is closed for modification but open for extension. In a notification service: define a `NotificationStrategy` interface with a `send()` method. Each channel (Email, SMS, Push) is a separate class implementing that interface. When you add WhatsApp, you add one new class — you do not touch the `NotificationService` at all. The Spring container collects all implementations at startup via `List<NotificationStrategy>` injection and builds a registry map.

### Q: "What's the difference between SLI, SLO, and SLA — and why is the SLA lower than the SLO?"
> SLI is the metric you measure: "99.2% of requests succeeded in the last 30 days." SLO is your internal target: "we aim for 99.9%." SLA is the contractual promise to customers: "we guarantee 99.5%." The SLA is set below the SLO deliberately — the gap is the error budget that engineers can spend on planned deployments and occasional incidents without breaching the customer contract. If the SLA equalled the SLO, every incident that touched the SLO would breach the SLA and trigger penalties.

### Q: "How does observability differ from just logging?"
> Logging is one pillar of observability — structured text events per request. Observability requires three pillars: logs (what happened), metrics (how often and how fast, aggregated over time — P99 latency, error rate), and traces (the full journey of one specific request across all services). The differentiator is correlation: without a shared trace_id, logs from ServiceA and ServiceB cannot be linked to the same user request. Observability means you can answer "exactly what happened for customer X's request that failed at 11:47 PM" without guessing.

### Q: "When DocuSign adds a new signature channel (e.g., in-person kiosk signing), which dimensions does that change touch and in what order?"
> First, Extensibility — the signing workflow must expose an interface that a new `KioskSigningStrategy` implements, with no changes to the core service. Second, Testability — the new strategy is injectable, so it can be unit-tested against a mock document repository. Third, Observability — the new channel needs its own metric tag (`channel=KIOSK`) so the team can monitor its error rate independently from existing channels. Security and Availability are unchanged because those live in the shared layers above the strategy. This ordering (Extensibility → Testability → Observability) is the standard sequence for plugging in any new behaviour. *(Tier 2 — cross-dimension)*

### Q: "A customer reports their signing ceremony failed at 11:47 PM on June 15th. It's now June 18th. How does your design let you investigate this?"
> Availability design means the incident was logged by a healthy replica even if one node was down. Observability design means: I query the log store for `traceId` where `timestamp` is between 11:45 and 11:49 PM for that `tenantId`. The trace_id from the error log gives me the full distributed trace — I can see which service returned a non-2xx response and at which hop. The structured log includes `signerId`, `documentId`, `ip_address`, and `error_code`. The audit trail (append-only event log) has an immutable record of every state transition. Security design means the audit trail is cryptographically signed — I can prove to the customer that the record has not been modified since the incident. *(Tier 2 — cross-dimension)*

### Q: "Your signing service handles 50K concurrent signing ceremonies during an IPO. Which dimension limits you and how do you address it?"
> The bottleneck is the write path — every signature creates an audit event, updates document state, and notifies signers. Under 50K concurrent ceremonies, the database write path becomes the toll booth. I address it by: (1) sharding the document state table by `envelope_id` hash so writes distribute across nodes, (2) offloading audit event writes to a Kafka topic with a consumer writing to a separate audit DB — this decouples latency-sensitive signing from the audit write throughput, and (3) adding a circuit breaker on the notification service so a downstream email provider slowdown doesn't cascade into signing timeouts. Scalability and Availability work together here. *(Tier 2 — cross-dimension)*

---

## 🧾 TL;DR — One Interviewer-Ready Line

> "DocuSign grades every design against seven qualities: Testability (inject deps as interfaces so components test in isolation), Usability (predictable API contract — right verbs, consistent error format), Extensibility (Open-Closed Principle — new behaviours plug in without modifying existing code), Security (defence in depth — authenticate, authorise, encrypt), Availability (SLO + redundancy + circuit breaker — 99.9% = 8.7 hrs downtime/year), Scalability (find the bottleneck, scale it specifically), and Observability (logs + metrics + traces with a shared trace_id) — and the senior signal is naming which two or three are primary for the specific question you're answering."

---

## 🔗 Related Concepts

- **`02-rate-limiting.md`** — Availability + Scalability in practice; a rate limiter that is itself a single point of failure fails the Availability dimension
- **`03-caching.md`** — core Scalability mechanism for read-heavy bottlenecks; see cache-aside and write-through
- **`07-cdc-outbox.md`** — Extensibility (outbox as a plug-in to any write path) + Observability (CDC events are the audit trail)
- **`13-security-pki.md`** — Security dimension in full depth: PKI, digital signatures, non-repudiation, audit trail
- **`14-document-blob-storage.md`** — Availability + Security dimensions applied to DocuSign's document storage architecture

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **"Service Level Objectives"** — Google SRE Book (sre.google) | The canonical SLI/SLO/SLA framework with the error budget concept in full depth — primary source for Availability | ~25 min read |
| **"What is Observability"** — ByteByteGo (YouTube) | Visual walkthrough of the three pillars (logs/metrics/traces) with concrete examples of what each catches | ~10 min |
| **OpenTelemetry Concepts** — opentelemetry.io | The vendor-neutral standard for distributed tracing: how trace_id, span_id, and context propagation work across service boundaries | ~20 min read |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | File created. Survey/reference format — covers all 7 DocuSign evaluation dimensions in parallel. Each dimension: one mental model analogy, one implementation pattern with Java code, one DocuSign-specific angle. Format intentionally deviates from the single-concept pattern of notes 01–14; deviation documented here per notes-standards.md guidance. |
