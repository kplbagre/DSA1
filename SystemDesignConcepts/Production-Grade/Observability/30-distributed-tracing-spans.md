# Distributed Tracing — Request Flow Across Services

> Distributed tracing follows a single request as it flows through multiple microservices. Each service creates a span (operation record). Spans are linked via trace ID, creating a waterfall view of latency. At SDE 3: you must know trace ID propagation, sampling strategies, and how to correlate spans across service boundaries.

---

## 🎯 Why This Matters

Request is slow (2 seconds). Which service is the bottleneck? Without tracing, you have 10 services to check. With tracing, you see: Order Service (100ms) → Payment Service (1800ms) → Inventory Service (50ms). You immediately know Payment is slow. Distributed tracing is the fastest way to root-cause latency in microservices. In interviews, candidates often confuse logging with tracing — logs capture events, traces capture request path.

---

## 📖 What is Distributed Tracing & Span? (Basics)

**Distributed Tracing** = following a single request as it travels through multiple services, recording timing at each step.

**Trace ID** = unique ID for ONE request (constant as it flows through services)

**Span** = a record of work done in ONE service
- Records: name, start time, duration, which service did it
- Examples:
  - "api-gateway-incoming" span (5ms)
  - "order-service.create" span (500ms)
  - "db.query" span (150ms)
  - "payment-service.charge" span (300ms)

**How it works:**
```
Request arrives with Trace ID: "abc123"
    ↓
Service A creates Span #1 (name: "serviceA.process", duration: 100ms)
    ↓ (forwards request with same Trace ID)
Service B creates Span #2 (name: "serviceB.process", duration: 200ms)
    ↓ (forwards request with same Trace ID)
Service C creates Span #3 (name: "serviceC.process", duration: 50ms)
    ↓
All spans sent to Jaeger/Zipkin with same Trace ID
    ↓
Jaeger assembles: Trace ID abc123 contains [Span#1, Span#2, Span#3]
Shows timeline: which service took how long
```

**Simple analogy:**
- You track a package with tracking ID "XYZ"
- At each hub, someone records: "XYZ arrived at 2pm, left at 3pm (1 hour here)"
- Final timeline: Hub A (1h) → Hub B (2h) → Hub C (30min) = 3.5h total
- You see Hub B took longest

---

## 📖 Terminology Table

| Term | Plain-English Meaning | Example |
|------|----------------------|---------|
| **Trace ID** | globally unique ID assigned to ONE request; same value propagated across all services it touches | `traceId: "abc123"` appears in every service's log for that single request |
| **Span** | one unit of work within a single service, recorded with start time + duration | `order-service.create` span: 500ms; `db.query` span: 150ms |
| **Parent Span** | span that triggered child spans in downstream services; forms a tree hierarchy | API Gateway span is parent; Order Service span and Payment span are children |
| **OpenTelemetry** | vendor-neutral standard for emitting traces, metrics, and logs; replaces Zipkin/Jaeger SDKs | instrument once with OpenTelemetry; export to Jaeger, Datadog, or Honeycomb |
| **Jaeger / Zipkin** | open-source backends that store and visualize trace data | Jaeger UI: waterfall diagram showing all spans for trace `abc123` |
| **Sampling** | only trace N% of requests to avoid overwhelming the tracing backend | 1% sampling = trace 1 in 100 requests; head sampling vs tail sampling |
| **W3C traceparent** | standard HTTP header for passing trace context between services | `traceparent: 00-abc123-spanid-01` — propagated automatically by OpenTelemetry |
| **Waterfall View** | trace visualization showing each span as a horizontal bar aligned on a timeline | gaps = idle wait time; overlapping bars = parallel calls |
| **Baggage** | key-value metadata attached to a trace and propagated with every span downstream | `user_id=42` in baggage → available in every downstream service without explicit passing |

---

## 🧠 The Mental Model

Imagine a parcel traveling through shipping hubs:

**Without tracing:**
- Parcel sent from warehouse A.
- You know it arrived in 5 days.
- But you don't know: warehouse B spent 2 days (slow), warehouse C spent 10 hours, warehouse D spent 2 hours.

**With tracing:**
- Parcel has tracking ID.
- Every hub stamps the parcel: "arrived at 3pm, left at 5pm" (2 hours here).
- You see the full journey: A(2h) → B(1d) → C(10h) → D(2h) = 5 days total.
- You immediately see B is the bottleneck (1 day vs 2-10 hours elsewhere).

**The key insight:** Tracing captures **timing of each step**, not just the outcome. Latency = sum of step latencies.

---

## 🎨 Visual — Distributed Tracing Architecture

### Full System Topology — Where Tracing Sits

```
CLIENT (e.g., Browser)
    ↓ (HTTP request with trace_id header)
┌──────────────────────────────────────────────────┐
│ API GATEWAY                                      │
│ ┌──────────────────────────────────────────────┐ │
│ │ 1. Extract trace_id from header              │ │
│ │ 2. Create span: name="gateway", duration=5ms│ │
│ │ 3. Propagate trace_id downstream             │ │
│ └──────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────┘
    ↓ (forward with X-Trace-ID header)
┌──────────────────────────────────────────────────┐
│ SERVICE #1: Order Service                        │
│ ┌──────────────────────────────────────────────┐ │
│ │ 1. Receive trace_id from header              │ │
│ │ 2. Create child span: name="order.create"    │ │
│ │ 3. Call Payment Service                      │ │
│ │ 4. Measure duration: 500ms (150ms in this    │ │
│ │    service, 350ms waiting for Payment)       │ │
│ │ 5. Send span to collector                    │ │
│ └──────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────┘
    ↓
┌──────────────────────────────────────────────────┐
│ SERVICE #2: Payment Service                      │
│ ┌──────────────────────────────────────────────┐ │
│ │ 1. Receive trace_id                          │ │
│ │ 2. Create child span: name="payment.charge"  │ │
│ │ 3. Query database                            │ │
│ │ 4. Duration: 300ms                           │ │
│ │ 5. Send span to collector                    │ │
│ └──────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────┘
    ↓
┌──────────────────────────────────────────────────┐
│ OBSERVABILITY LAYER (Jaeger / Zipkin)            │
│ ┌──────────────────────────────────────────────┐ │
│ │ Collector receives spans from all services   │ │
│ │ - API Gateway span (5ms)                     │ │
│ │ - Order Service span (500ms)                 │ │
│ │ - Payment Service span (300ms)               │ │
│ │ Assembles trace: trace_id = abc123           │ │
│ │ Builds waterfall (parent-child relationships)│ │
│ │ Stores in backend (Elasticsearch, Cassandra) │ │ 
│ └──────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────┘
    ↓
┌──────────────────────────────────────────────────┐
│ VISUALIZATION (Jaeger UI / Grafana)              │
│ User views trace: timeline of all spans          │
│ Identify bottleneck: Payment Service 300ms       │
└──────────────────────────────────────────────────┘

KEY INVARIANT:
   Single trace_id follows request across services.
   Each service creates child span (linked to parent).
   Span carries: name, start, duration, tags, logs.
   Collector assembles: parent → child hierarchy.
```

### Component Detail — Trace Propagation & Span Hierarchy

```
TRACE STRUCTURE:

Trace ID: abc123def456 (unique per request)
│
├─ Span #0: name="api-gateway-incoming"
│  ├─ start: 1719259200.000
│  ├─ duration: 5ms
│  ├─ tags: {method: "POST", path: "/orders", status: 200}
│  └─ logs: [entered gateway, exiting gateway]
│
├─ Span #1: name="order-service.create" (parent = Span #0)
│  ├─ start: 1719259200.005
│  ├─ duration: 500ms
│  ├─ tags: {service: "order-service", operation: "create"}
│  │
│  └─ Span #1.1: name="db.query" (parent = Span #1)
│     ├─ start: 1719259200.010
│     ├─ duration: 150ms
│     ├─ tags: {db: "postgresql", query: "INSERT orders"}
│     └─ logs: [query started, query completed]
│
├─ Span #2: name="payment-service.charge" (parent = Span #0)
│  ├─ start: 1719259200.150 (concurrent with Span #1.1)
│  ├─ duration: 300ms
│  ├─ tags: {service: "payment-service", operation: "charge"}
│  │
│  └─ Span #2.1: name="external-api.visa" (parent = Span #2)
│     ├─ start: 1719259200.155
│     ├─ duration: 280ms
│     ├─ tags: {external: "visa-api", endpoint: "https://..."}
│     └─ logs: [API called, response received]


WATERFALL VISUALIZATION (Timeline):

Time (ms)
0      ├─────────────────────────────────────────┤
       │ Span #0: Gateway (5ms)                  │
5      │                                         │
       │ Span #1: Order Service (500ms)          │
       │ ├─ Span #1.1: DB Query (150ms)          │
10     │ │ ├─────────────────┤                   │
160    │ │                   │                   │
       │ │                   Span #2: Payment (300ms)
       │ │                   ├─ Span #2.1: Visa API (280ms)
       │                     │   ├─────────────────────────┤
300    │                     │                             │
       │                     │ Waiting for response       │
500    │ Response ready (both DB + Payment done)          │

TRACE PROPAGATION (Trace ID across services):

Client → API Gateway:
  Request headers:
    X-Trace-ID: abc123def456
    X-Span-ID: span0
    X-Parent-Span-ID: (none, root)

API Gateway → Order Service:
  Forwards request with:
    X-Trace-ID: abc123def456 (same)
    X-Span-ID: span1 (new span for this service)
    X-Parent-Span-ID: span0 (links to gateway)

Order Service → Payment Service:
  Forwards request with:
    X-Trace-ID: abc123def456 (same)
    X-Span-ID: span2 (new span for payment service)
    X-Parent-Span-ID: span0 (could also be span1 if sequential)

KEY PRINCIPLE: trace_id stays the same across all services.
Each service creates new span, links to parent via parent_span_id.
```

---

## ⚙️ How It Actually Works

**Steps in plain English:**

1. **Client makes request** (HTTP GET /orders).
2. **API Gateway generates trace ID** (if not present) or extracts from header.
3. **Gateway injects trace ID** into request headers (X-Trace-ID).
4. **Gateway creates span** for its own work (name="gateway-incoming").
5. **Gateway forwards request** to Order Service with trace ID header.
6. **Order Service receives** trace ID, creates child span (parent = gateway span).
7. **Order Service calls Payment Service** with trace ID.
8. **Payment Service receives** trace ID, creates child span.
9. **Each service measures duration** of its work.
10. **Each service sends span** to trace collector (Jaeger agent).
11. **Collector receives spans**, assembles into trace (matches by trace_id).
12. **Trace is stored** and can be viewed in UI.

```java
// Distributed Tracing with OpenTelemetry

@Configuration
public class TracingConfiguration {
    @Bean
    public OpenTelemetry openTelemetry() {
        // Step 10 — Configure trace exporter (Jaeger)
        OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
            .setEndpoint("http://jaeger-collector:4317")
            .build();

        // Step 12 — Configure storage backend
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
            .setResource(Resource.getDefault()
                .merge(Resource.create(Attributes.of(
                    ResourceAttributes.SERVICE_NAME, "order-service"))))
            .setSampler(Sampler.alwaysOn())  // Sample all traces (100%)
            .build();

        return OpenTelemetry.builder()
            .setTracerProvider(tracerProvider)
            .build();
    }
}

// Service Implementation with Tracing

@Service
public class OrderService {
    @Autowired
    private Tracer tracer;  // OpenTelemetry tracer

    // Step 6-9 — Create spans for service operations
    public void createOrder(CreateOrderRequest request) {
        // Step 6 — Trace ID is already in request context (via filter)
        // Step 6 — Create span for this service
        Span span = tracer.spanBuilder("order-service.create")
            .setParent(Context.current())
            .startSpan();

        try (Scope scope = span.makeCurrent()) {
            // Step 6 — Span is now active in context
            span.setAttribute("order.customer_id", request.getCustomerId());
            span.setAttribute("order.amount", request.getAmount());

            // Step 7 — Call database
            saveOrder(request);

            // Step 9 — Call payment service
            chargePayment(request);
        } finally {
            // Step 9 — Span duration is measured automatically
            span.end();  // Span is complete; duration captured
        }
    }

    // Step 7 — Database operation with nested span
    private void saveOrder(CreateOrderRequest request) {
        Span dbSpan = tracer.spanBuilder("db.query")
            .setParent(Context.current())
            .startSpan();

        try (Scope scope = dbSpan.makeCurrent()) {
            dbSpan.setAttribute("db.system", "postgresql");
            dbSpan.setAttribute("db.statement", "INSERT INTO orders");

            // Execute query
            orderRepository.save(new Order(request));
        } finally {
            dbSpan.end();  // Duration includes query execution time
        }
    }

    // Step 8 — Call external service with trace propagation
    private void chargePayment(CreateOrderRequest request) {
        Span paymentSpan = tracer.spanBuilder("payment-service.charge")
            .setParent(Context.current())
            .startSpan();

        try (Scope scope = paymentSpan.makeCurrent()) {
            paymentSpan.setAttribute("payment.amount", request.getAmount());

            // Step 8 — Propagate trace ID to payment service
            // OpenTelemetry automatically injects trace ID into HTTP headers
            HttpRequest paymentRequest = HttpRequest.newBuilder()
                .uri(new URI("http://payment-service/charge"))
                .POST(HttpRequest.BodyPublishers.ofString(request.toJson()))
                .build();

            // OpenTelemetry injects headers:
            //   traceparent: 00-abc123def456-span2-01
            //   (format: version-trace_id-span_id-trace_flags)

            HttpResponse response = httpClient.send(paymentRequest, 
                HttpResponse.BodyHandlers.ofString());

            paymentSpan.setAttribute("payment.status", response.statusCode());
        } finally {
            paymentSpan.end();
        }
    }
}

// API Gateway Filter (Trace ID extraction and injection)

@Component
public class TracingFilter implements WebFilter {
    @Autowired
    private Tracer tracer;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // Step 2 — Extract trace ID from incoming request
        String traceId = extractTraceId(exchange.getRequest());
        
        if (traceId == null) {
            // Step 2 — Generate new trace ID
            traceId = UUID.randomUUID().toString();
        }

        // Step 3 — Inject trace ID into request context
        return chain.filter(exchange.mutate().build())
            .contextWrite(Context.of("trace_id", traceId))
            .doOnNext(response -> {
                // Step 4 — Create gateway span
                Span gatewaySpan = tracer.spanBuilder("gateway-incoming")
                    .startSpan();

                try (Scope scope = gatewaySpan.makeCurrent()) {
                    gatewaySpan.setAttribute("http.method", 
                        exchange.getRequest().getMethod().toString());
                    gatewaySpan.setAttribute("http.path", 
                        exchange.getRequest().getPath().value());

                    // Step 5 — Forward to service (trace ID in headers)
                    // OpenTelemetry injects automatically
                } finally {
                    gatewaySpan.end();
                }
            });
    }

    private String extractTraceId(ServerHttpRequest request) {
        // Look for traceparent header (W3C standard)
        String traceparent = request.getHeaders().getFirst("traceparent");
        if (traceparent != null) {
            // Format: version-trace_id-span_id-trace_flags
            String[] parts = traceparent.split("-");
            return parts[1];  // Extract trace_id
        }
        return null;
    }
}

// Sampling Configuration (reduce overhead at high throughput)

@Configuration
public class SamplingConfiguration {
    @Bean
    public Sampler customSampler() {
        // Sample only 10% of requests (reduce storage and latency overhead)
        return Sampler.parentBased(
            Sampler.traceIdRatioBased(0.1)  // 10% sampling
        );
    }

    // For error traces, always sample (100% for errors, 10% for success)
    @Bean
    public Sampler errorPrioritySampler() {
        return new Sampler() {
            @Override
            public SamplingResult shouldSample(Context parentContext, 
                    String traceId, String name, SpanKind spanKind, 
                    Attributes attributes, List<LinkData> parentLinks) {
                // Check if this is an error span
                if (attributes != null && attributes.get(AttributeKey.booleanKey("is_error")) == true) {
                    return SamplingResult.recordAndSample();  // 100% sample errors
                }
                return SamplingResult.recordAndSample();  // 10% sample others
            }
        };
    }
}
```

### What is Traceparent Header, and why does it fit here?

Traceparent is a **W3C standard header** for distributed tracing: `traceparent: 00-{trace_id}-{span_id}-{trace_flags}`. It standardizes how trace context is propagated across services (languages, frameworks). In an interview, if asked: *"Traceparent is the W3C standard for propagating trace context. Instead of custom headers (X-Trace-ID), we use traceparent which all observability tools understand. It contains trace ID (constant across request), span ID (unique per service), and flags (sampled or not)."*

---

## 🏢 Real World — Where Companies Use This

- **Google (Dapper, evolved to OpenTelemetry):** Every RPC call in Google traced with latency breakdown. With 10,000 services, distributed tracing is non-negotiable.
- **Uber (Jaeger):** Trace every trip request end-to-end: dispatcher → matching → driver app → payments. Latency analysis per region.
- **Netflix (X-Ray + custom):** Traces video streaming request across CDN → microservices. Identifies if issue is CDN, origin, or encoding service.
- **Stripe (custom tracing):** Payment request traced through fraud detection → payment processor → settlement. Latency SLA: p99 < 500ms.
- **Datadog (observability platform):** Datadog's APM (application performance monitoring) is built on distributed tracing — customers send spans via Datadog SDK.

---

## 🧭 When to Use vs When NOT to Use

| Use distributed tracing when | Do NOT use when |
|---|---|
| Microservices architecture (multiple services) | Monolith (single service, use logs/metrics) |
| Need to root-cause latency across services | Response time is already good |
| Have hundreds/thousands of requests/sec (sampling) | Tiny traffic volume (logs sufficient) |
| Want timeline of request flow | Only need aggregate metrics ("average latency") |
| Multiple independent teams managing services | Tightly coupled system |

**The common mistake:** Tracing all requests (100% sampling) at high throughput. This generates millions of spans/sec, overloading collector and storage. Use sampling (1-10% for normal, 100% for errors).

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | Root-cause latency instantly (see slowest span). Distributed view of request flow (context across services). Error correlation (error trace shows full path). Performance baseline (p50, p95, p99 latency per span). |
| **You lose** | Operational overhead (run trace collector, storage backend). Sampling reduces visibility (1% sampling means you miss 99% of traces). Network overhead (each service sends spans to collector). Storage cost (traces are large; 1M traces/day = TB of storage). |
| **Failure mode** | Trace collector crashes → spans are lost (no history of latency). Sampling too aggressive → rare issues missed. Trace ID not propagated → spans disconnected (trace incomplete). Mitigation: highly available collector, error-priority sampling (always trace errors), automated trace ID injection. |

---

## 🔬 Interview Q&As

### Q: "Your system has 1M requests/sec. Tracing all requests (100% sampling) generates 10M spans/sec. Collector can't keep up. How do you handle?"

> Use sampling: trace only 1% of requests (10K spans/sec). For rare errors, oversample: trace 100% of errors but only 1% of success requests. Or use tail-based sampling: sample based on latency (if p99 latency spike, trace all requests from that minute). ⭐ **Tier 2 — Scaling**

### Q: "Service A calls Service B. Trace ID is propagated. Service B has an error. How do you correlate the error log with the trace?"

> Error logs include trace_id (injected via MDC). Search logs by trace_id: grep "trace_id=abc123" → find error log. Query Jaeger by trace_id=abc123 → see latency waterfall of the request. Logs + traces cross-reference via trace_id. ⭐ **Tier 2 — Correlation**

### Q: "Span shows Order Service took 500ms but actual code execution was only 150ms. Where's the other 350ms?"

> Queuing/waiting. Order Service is waiting for downstream service (Payment Service) to respond (350ms). Span duration = wall-clock time (includes waits). To see breakdown: look at child spans. If Payment Service span is 300ms, you're waiting 300ms on that call. ⭐ **Tier 2 — Latency analysis**

### Q: "How do you prevent trace ID leakage (sensitive info in trace)?"

> Don't include user passwords, API keys, or PII in span attributes. If needed, redact in span processor before sending to collector. Use tag allowlist: only specific tags (method, status code, latency) are permitted. ⭐ **Tier 2 — Security**

---

## 🧾 TL;DR

> "Distributed tracing follows single request across services via trace ID (constant). Each service creates span with duration. Spans linked parent-child, forming timeline. Waterfall visualization shows which service is bottleneck. Sampling (1-10%) reduces overhead at high throughput."

---

## 🔗 Related Concepts

- **`25-monitoring-observability-fundamentals.md`** — Distributed tracing is one of three pillars (logs, metrics, traces)
- **`24-api-gateway-pattern.md`** — Gateway injects trace ID at entry point
- **`20-circuit-breaker-resilience.md`** — Circuit breaker changes tracked in traces

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **OpenTelemetry Documentation** | Tracing specification, context propagation, sampling strategies | ~25 min read |
| **Jaeger Documentation** | Span model, trace storage, sampling algorithms, UI navigation | ~20 min read |
| **Arpit Bhayani — Distributed Tracing** (YouTube) | Real-world latency analysis, root-cause examples, tool comparison | ~20 min |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 25, 2026 | Created as Concept 30. Covered distributed tracing architecture, trace ID propagation across services, span hierarchy, sampling strategies (constant, probabilistic, error-priority), latency waterfall visualization, OpenTelemetry integration. |
