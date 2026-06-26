# Monitoring & Observability — Three Pillars (Logs, Metrics, Traces)

> Observability is the ability to ask ANY question about your system and answer it — even questions you didn't anticipate. Monitoring is the infrastructure: logs capture what happened, metrics measure how much/often, traces show request flow. At SDE 3: you must know the three pillars, how they differ, and why you need all three — not just pick one.

---

## 🎯 Why This Matters

Your service is slow. Is it: (1) database query taking 500ms? (2) network latency to external API? (3) GC pause? (4) all three, intermittently? Without observability, you're blind. With it, you correlate logs (what queries ran), metrics (GC time, latency percentiles), and traces (request path through services) to pinpoint the issue in 5 minutes instead of 5 hours. In interviews, candidates often conflate "logs" with "observability"; you'll explain the three pillars and when to use each.

---

## 🧠 The Mental Model

Imagine you're a restaurant manager. A customer complains: "My order took too long."

**Logs (What happened):**
- Order #42 received at 14:00
- Sent to kitchen at 14:01
- "No more beef" message logged at 14:05
- Changed to chicken, plated at 14:10
- Delivered at 14:12
- *Tells you the STORY*

**Metrics (How much/often):**
- Average order-to-plate time: 11 minutes
- 95th percentile: 25 minutes
- Beef orders rejected: 3 in last hour (out of 10 beef orders = 30%)
- Kitchen utilization: 85%
- *Tells you the PATTERNS*

**Traces (Path through system):**
- Customer app → order service (2ms) → payment service (150ms) → kitchen service (600ms) → delivery app (50ms)
- Total: 802ms
- Payment service is SLOW — dig into its database query
- *Tells you the BOTTLENECK*

**The key insight:** Logs answer "what happened?"; Metrics answer "how much?"; Traces answer "where's the bottleneck?" You need all three to move from "we have a problem" to "here's the root cause."

---

## 🎨 Visual — Observability Architecture

### Full System Topology — Where Observability Sits (OUTSIDE Main Request Path)

```
┌────────────────────────────────────────────────────────────────┐
│                    CLIENT / INTERNET                           │
└────────────────────────────────────────────────────────────────┘
    ↓
┌────────────────────────────────────────────────────────────────┐
│ API GATEWAY                                                    │
│ [Route] [Auth] [Rate Limit]                                  │
│ → Emit logs, metrics (latency, 200 vs 429 vs 401)            │
│ → Create trace_id, inject into request header                │
└────────────────────────────────────────────────────────────────┘
    ↓                                    ↗ (emit trace, metrics)
┌────────────────────────────────────────────────────────────────┐
│ LOAD BALANCER                                                  │
└────────────────────────────────────────────────────────────────┘
    ↓         ↓         ↓
   ┌──────┐ ┌──────┐ ┌──────┐
   │ Pod1 │ │ Pod2 │ │ Pod3 │
   │Order │ │User  │ │Catalog
   │Svc   │ │Svc   │ │Svc
   └──────┘ └──────┘ └──────┘
    ↓ (structured logs via SLF4J/SLF4J)
    ↓ (JVM metrics via Micrometer)
    ↓ (request trace via OpenTelemetry)
    ↓
┌──────────────────────────────────────────────────────────────────┐
│                   OBSERVABILITY LAYER                           │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ LOGS PIPELINE           → Elasticsearch  → Kibana          │ │
│  │ (application events)      (full-text search)  (dashboard)  │ │
│  └────────────────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ METRICS PIPELINE        → Prometheus     → Grafana         │ │
│  │ (counters, gauges)        (time-series)     (visualize)    │ │
│  └────────────────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ TRACES PIPELINE         → Jaeger / Zipkin→ Timeline view   │ │
│  │ (request flow)            (correlation)    (span waterfall)│ │
│  └────────────────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ ALERTING LAYER → AlertManager → PagerDuty / Email         │ │
│  │ (Rule: if P99 latency > 500ms, fire alert)                │ │
│  └────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘

KEY INVARIANT:
   Observability is PARALLEL to main request path, not in the critical path.
   Services emit telemetry; collectors and storage are decoupled.
   If logging system fails, it doesn't affect request serving (but you go blind).
```

### Component Detail — Three Pillars Compared

```
LOGS vs METRICS vs TRACES:

┌─────────────────────────────────────────────────────────────────┐
│ LOGS (ELK Stack)                                                │
│ Event: "OrderService received order_id=42 amount=500.00 at 14:05:02.123"
│                                                                  │
│ Structure: timestamp, service, level (ERROR/WARN/INFO), message │
│ Format: JSON (structured) or text                               │
│ Query: "Find all ERROR logs from OrderService last 1 hour"     │
│ Use: Debugging ("why did order fail?")                          │
│ Volume: HIGH (millions per second at scale)                     │
│ Retention: weeks to months                                      │
│ Storage: Elasticsearch (full-text searchable)                  │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ METRICS (Prometheus + Grafana)                                  │
│ Time-series: http_request_latency_ms = 245 (at timestamp 14:05) │
│ Time-series: http_requests_total = 50000 (counter, cumulative)  │
│ Time-series: jvm_memory_used_bytes = 1073741824 (gauge, point) │
│                                                                  │
│ Structure: metric_name{label1=value1, label2=value2} = value   │
│ Example: response_time{service="order",endpoint="/orders"} = 250
│ Query: "What's the 95th percentile latency for /orders?"       │
│ Use: Monitoring trends ("latency increasing over time?")        │
│ Volume: MEDIUM (thousands per second, aggregated)               │
│ Retention: days to years (compressed)                           │
│ Storage: Prometheus (time-series DB)                            │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ TRACES (Jaeger / Zipkin)                                        │
│ Trace for single request: trace_id = "abc123def456"             │
│   ├─ Span 1: API Gateway (2ms)                                  │
│   │  └─ Span 2: Order Service (700ms)                           │
│   │     └─ Span 3: Database Query (650ms) ← BOTTLENECK          │
│   │     └─ Span 4: Inventory Check (45ms)                       │
│   └─ Span 5: Payment Service (100ms)                            │
│ Total: 802ms                                                    │
│                                                                  │
│ Structure: trace_id, span_id, parent_span_id, duration, events │
│ Query: "Show me the request path for trace_id=abc123"           │
│ Use: Debugging (where's the latency?), distributed debugging    │
│ Volume: LOW (each request = 1 trace, sampled at 1-10%)         │
│ Retention: days                                                 │
│ Storage: Jaeger (trace database)                                │
└─────────────────────────────────────────────────────────────────┘

DECISION TABLE: Use which pillar for what?
┌──────────────────────────────────────────────────────────────┐
│ Question                    → Use This Pillar                 │
├──────────────────────────────────────────────────────────────┤
│ "Why did order #42 fail?"   → LOGS (event story)            │
│ "What's our error rate?"    → METRICS (% of requests)       │
│ "Where's the latency?"      → TRACES (request path)         │
│ "Which DB query is slow?"   → LOGS (detailed query text)    │
│ "How many failed orders?" → METRICS (counter)                │
│ "Show me the exact sequence"→ TRACES (waterfall)             │
└──────────────────────────────────────────────────────────────┘

KEY INVARIANT:
   Logs: Rich detail about individual events (high cardinality)
   Metrics: Aggregated patterns over time (low cardinality, queryable)
   Traces: Request journey through services (sampled, causality)
   All three are needed; they answer different questions.
```

---

## ⚙️ How It Actually Works

**Steps in plain English:**

1. **Service generates telemetry** — logs (SLF4J), metrics (Micrometer), traces (OpenTelemetry).
2. **Logs are pushed/pulled to ELK** — Logstash parses JSON, sends to Elasticsearch.
3. **Metrics are scraped by Prometheus** — Prometheus polls `/metrics` endpoint every 15 seconds.
4. **Traces are collected by Jaeger agent** — local agent buffers spans, flushes to Jaeger collector.
5. **Elasticsearch indexes logs** — full-text search, query via Kibana.
6. **Prometheus stores time-series** — query via Grafana dashboards.
7. **Jaeger stores trace data** — query via Jaeger UI (show timeline of spans).
8. **Alertmanager fires rules** — if Prometheus query `P99_latency > 500ms`, fire PagerDuty alert.

```java
// Service Instrumentation (Logs + Metrics + Traces)

@Service
public class OrderService {
    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);
    private final MeterRegistry meterRegistry;
    private final Tracer tracer;

    // Step 1-2 — Logging with MDC (Mapped Diagnostic Context for trace_id)
    public Order createOrder(CreateOrderRequest request) {
        // Extract trace_id from incoming request headers
        String traceId = request.getHeaders().get("X-Trace-ID");
        MDC.put("trace_id", traceId);
        MDC.put("user_id", request.getUserId());

        try {
            logger.info("Creating order for user_id={}, amount={}", 
                request.getUserId(), request.getAmount());
            
            // Step 3 — Metrics: counter for order creation attempts
            meterRegistry.counter("orders.created.attempts").increment();

            // Step 4 — Traces: create span for this operation
            Span span = tracer.startSpan("order.create");
            try {
                Order order = new Order();
                order.setId(UUID.randomUUID().toString());
                order.setAmount(request.getAmount());
                order.setStatus("PENDING");

                // Database call with latency metric
                long startTime = System.currentTimeMillis();
                Order saved = orderRepository.save(order);
                long latency = System.currentTimeMillis() - startTime;

                // Step 3 — Metrics: histogram (latency distribution)
                meterRegistry.timer("orders.save.latency").record(latency, TimeUnit.MILLISECONDS);

                // Step 1-2 — Log success
                logger.info("Order created successfully: order_id={}, latency_ms={}", 
                    saved.getId(), latency);
                
                // Step 3 — Metrics: counter for successful orders
                meterRegistry.counter("orders.created.success").increment();

                return saved;
            } finally {
                span.finish();
            }
        } catch (Exception e) {
            // Step 1-2 — Log error with stack trace
            logger.error("Failed to create order", e);
            
            // Step 3 — Metrics: counter for failed orders
            meterRegistry.counter("orders.created.failures").increment();
            
            throw e;
        } finally {
            MDC.clear(); // Clean up MDC
        }
    }

    // Step 1-2 — Call external service with trace propagation
    private void chargePayment(Order order) {
        // Step 4 — Traces: create child span for payment call
        Span paymentSpan = tracer.startSpan("payment.charge", tracer.activeSpan());
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI("http://payment-service/charge"))
                .header("X-Trace-ID", MDC.get("trace_id")) // Step 4 — Propagate trace_id
                .POST(HttpRequest.BodyPublishers.ofString(order.toJson()))
                .build();

            long startTime = System.currentTimeMillis();
            HttpResponse response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - startTime;

            // Step 1-2 — Log payment result
            logger.info("Payment charged: order_id={}, latency_ms={}, status={}", 
                order.getId(), latency, response.statusCode());
            
            // Step 3 — Metrics: external service latency
            meterRegistry.timer("payment.charge.latency").record(latency, TimeUnit.MILLISECONDS);
            
            if (response.statusCode() != 200) {
                // Step 1-2 — Log failure detail
                logger.warn("Payment failed: order_id={}, status={}, response={}", 
                    order.getId(), response.statusCode(), response.body());
                
                // Step 3 — Metrics: failure counter
                meterRegistry.counter("payment.charge.failures").increment();
            }
        } catch (Exception e) {
            // Step 1-2 — Log exception
            logger.error("Payment service error for order_id={}", order.getId(), e);
            
            // Step 3 — Metrics: error counter
            meterRegistry.counter("payment.charge.errors").increment();
            
            throw e;
        } finally {
            paymentSpan.finish();
        }
    }
}

// Spring Boot Actuator Configuration (exposes /metrics endpoint)
@Configuration
public class MetricsConfig {
    @Bean
    public MeterBinder customMetrics() {
        return (registry) -> {
            // Step 3 — Custom gauges (point-in-time measurements)
            Gauge.builder("orders.pending.count", () -> orderRepository.countByStatus("PENDING"))
                .description("Number of pending orders")
                .register(registry);
        };
    }
}

// Logging Configuration (JSON structured logs to stdout → Logstash → Elasticsearch)
// logback.xml
/*
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
    </appender>
    <root level="INFO">
        <appender-ref ref="STDOUT"/>
    </root>
</configuration>
*/

// OpenTelemetry Configuration (trace collection)
@Configuration
public class TracingConfig {
    @Bean
    public OpenTelemetry openTelemetry() {
        // Jaeger exporter sends traces to Jaeger collector
        OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
            .setEndpoint("http://jaeger-collector:4317")
            .build();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
            .setResource(Resource.getDefault()
                .merge(Resource.create(Attributes.of(
                    ResourceAttributes.SERVICE_NAME, "order-service"))))
            .build();

        return OpenTelemetry.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(DefaultContextPropagators.create())
            .build();
    }
}
```

### What is MDC (Mapped Diagnostic Context), and why does it fit here?

MDC is a **thread-local map** that stores context values (trace_id, user_id, request_id) automatically injected into every log statement in that thread without explicit parameter passing. In an interview, if asked: *"MDC allows us to correlate logs across service calls by storing the trace_id in a thread-local context. Every log message automatically includes the trace_id, so when we query Elasticsearch for 'trace_id=abc123', we see the ENTIRE story — logs from gateway, order service, payment service — in sequence. Without MDC, we'd have to manually pass trace_id to every log call."*

### What is OpenTelemetry, and why does it fit here?

OpenTelemetry is an **open standard for collecting traces, metrics, and logs** from applications. It's vendor-agnostic — you can send traces to Jaeger, Zipkin, Datadog, or any backend. In an interview, if asked: *"OpenTelemetry is the industry standard for distributed tracing. It provides instrumentation libraries (auto-inject spans around HTTP calls, database queries), a collector agent (buffers and exports telemetry), and integration with storage backends like Jaeger. We use it because it's an open standard — not tied to a single vendor, and it works across Java, Python, Go, etc."*

---

## 🏢 Real World — Where Companies Use This

- **Google (Dapper, evolved to OpenTelemetry):** Traces every request across thousands of services. Latency breakdown: if p99 is 500ms but p50 is 50ms, something is wrong in the tail — they dig into those outlier traces to find cascading failures or resource contention.
- **Uber (Jaeger):** Traces every trip request. Distributed query across order service → payment service → driver-match service → mapping service. If a driver matching takes 5 seconds, Jaeger shows exactly which service in the chain is slow. Metrics also show driver availability trends over time.
- **Swiggy (ELK for logs, Prometheus for metrics):** Logs all order events (created, assigned, accepted by restaurant, out for delivery). Metrics track order acceptance rate per restaurant — if a restaurant's acceptance rate drops, alert sent. Traces show order flow latency: if it's slow, which service is the bottleneck (restaurant service, delivery partner matching, payment)?
- **Stripe (Datadog for everything):** Every API call traced. Metrics for payment success rate per card type, per region, per processor. Logs contain detailed payment flow (authorization, settlement, webhook sent). All three pillars used in real-time alerting: if success rate drops below 99.5%, PagerDuty fires.
- **DoorDash (custom observability):** Metrics for delivery time buckets (0-5min, 5-10min, 10-15min, 15+min), tracked per restaurant. Traces show delivery lifecycle (order placed → accepted → prepared → picked up → in transit → delivered). Logs capture edge cases (customer no-answer, restaurant closed).

---

## 🧭 When to Use vs When NOT to Use

| Use observability when | Do NOT use when |
|---|---|
| You have multiple services or async processing | Monolith with synchronous call path (though still useful) |
| You need to debug latency or error rate spikes | You're just counting total requests (single counter might suffice) |
| You have intermittent failures hard to reproduce | You're building a prototype (overkill, add later) |
| You want to track trends (is latency increasing over time?) | You only care about current state, not history |
| You need causality (which service caused the error?) | You have a simple linear request path (still useful, but less critical) |

**The common mistake:** Logging everything at INFO level. This generates so much noise that you can't find the signal. Log INFO for important business events (order created, payment charged); log DEBUG only for dev environments; log WARN/ERROR for actual problems.

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | Root cause diagnosis (logs + metrics + traces pinpoint issues). Trend detection (is latency increasing over weeks?). Alerting (fire alarm before customer complains). Compliance audit trail (what happened, when, by whom). Performance debugging (resource bottleneck analysis). |
| **You lose** | Operational overhead (ELK stack, Prometheus, Jaeger are separate services to run). Data storage cost (logs can be TB/day at scale). Performance impact on application (instrumentation adds latency, though minimal with async). Complexity (query language for logs ≠ query language for metrics). |
| **Failure mode** | Observability system crashes → you go blind but requests still work (decoupled from critical path). But you also can't debug issues. Volume spike (millions of logs/sec) → storage cost explodes, pipeline backs up. Oversampling traces → storage fills up. Mitigation: log sampling strategy, trace sampling (1-10% of requests), log retention policy (delete after 30 days), metrics compression. |

---

## 🔬 Interview Q&As

### Q: "You're getting an alert: 'P99 latency = 750ms (should be <500ms)'. Where do you start investigating?"

> Start with metrics: plot latency over time — is it a gradual increase or sudden spike? If spike, check metrics around the same time (did CPU/memory spike? Did request volume spike? Did a new service version deploy?). Then check traces (sample 10 slow requests, show their span timeline — is it database slow? network? GC pause?). Then check logs (search for ERROR in same time window). Metrics tell you WHEN and roughly WHAT; traces tell you WHERE; logs tell you WHY. ⭐ **Tier 2 — Troubleshooting**

### Q: "Trace sampling: why not trace 100% of requests?"

> Cost and cardinality explosion. At 1M requests/sec, tracing 100% = 1M traces/sec. Each trace has 5-10 spans, each span is ~1KB. That's 5-10GB/sec of storage. Jaeger can't handle it; Elasticsearch would cost thousands/month. Solution: sample 1-10% of requests (Jaeger adaptive sampler samples more during low traffic, less during high traffic). Plus, traces are mostly duplicates — tracing 1% captures the patterns. ⭐ **Tier 2 — Scaling**

### Q: "Your MDC includes `user_id=123`. Later, you find that user 123's requests are slow. How do you correlate?"

> Query Elasticsearch: `user_id=123 AND log_level=ERROR` → find any errors from that user. Query Grafana: filter metrics by user_id label → see their request latency vs overall latency. Query Jaeger: `tag:user_id=123` → show all traces for that user → find the slowest traces. The three pillars work together: logs show WHAT happened to that user, metrics show HOW MUCH latency they experienced, traces show WHERE the latency comes from. ⭐ **Tier 2 — Correlation**

### Q: "You want to add a new metric: 'How many orders are pending and waiting for payment?' Is this a counter, gauge, or histogram?"

> This is a **gauge** (point-in-time value). Gauge is CURRENT value, snapshot. Counter is cumulative (never decreases). Histogram is distribution. Pending orders RIGHT NOW = gauge = `COUNT(*) FROM orders WHERE status='PENDING'`. Total orders created EVER = counter. Order latency distribution = histogram (0-5ms, 5-10ms, etc.). ⭐ **Tier 1 — Metrics design**

### Q: "You log: `logger.info("Processing order: {}", order.toJson())`. Under high load, this line alone generates 1GB/day of logs. How do you fix?"

> Two approaches: (1) **Log sampling** — `if (Math.random() < 0.01) logger.info(...)` → log only 1% of orders. (2) **Structured logging** — instead of JSON dump, log only critical fields: `logger.info("Processing order", event("order.create", Fields("order_id", id, "amount", amount)))` → reduces log size to 100 bytes per order instead of 10KB. ⭐ **Tier 2 — Performance**

### Q: "Your Prometheus instance crashed. You lost 2 days of metrics. How does this affect your alerting?"

> Prometheus stores historical data; alerts are based on that history (e.g., "if latency was >500ms for 5 min, fire alert"). If Prometheus crashed 2 days ago but just recovered, you've lost 2 days of history, but you can still query the last day and fire alerts going forward. However, you can't detect slow degradation over 2 days (might not notice if latency crept up gradually). Mitigation: use remote storage (Prometheus → TSDB like Cortex) so history survives Prometheus crashes. ⭐ **Tier 2 — Resilience**

### Q: "How do you distinguish between a real alert (something is broken) and a false positive (alert fired but system is fine)?"

> Define **alert SLA**: if alert fires, on-call engineer should be able to action it within 15 min and resolve within 1 hour. If alert fires but issue takes 8 hours to resolve (or is false), the alert threshold is wrong. Example: "P99 latency > 500ms" might be too strict (fires every week, but users don't notice). Change to "P99 latency > 1000ms for 5 min". Use burn rate: if latency is barely over threshold (like 505ms), don't fire; if it's clearly bad (1500ms), fire immediately. ⭐ **Tier 2 — Alert tuning**

---

## 🧾 TL;DR

> "Observability = three pillars: Logs (what happened?), Metrics (how much?), Traces (where's the bottleneck?). All three are needed to diagnose issues. Logs stored in Elasticsearch (queryable), Metrics in Prometheus (time-series), Traces in Jaeger (request causality). Correlate via trace_id (MDC) injected in logs."

---

## 🔗 Related Concepts

- **`15-system-qualities.md`** — SLI/SLO/SLA are defined using observability
- **`20-circuit-breaker-resilience.md`** — circuit breaker changes should be visible in metrics and traces
- **`24-api-gateway-pattern.md`** — gateway logs all requests; all logs should have trace_id
- **`19-message-queues-kafka-rabbitmq.md`** — Kafka consumer lag is a critical metric

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **Google SRE Book — "Monitoring Distributed Systems"** | Authoritative guide on SLI/SLO, monitoring philosophy, error budgets | ~30 min read |
| **ByteByteGo — "Observability: Logs, Metrics, Traces"** (YouTube) | Visual walkthrough of three pillars, when to use each, real-world architecture | ~12 min |
| **OpenTelemetry Documentation** | Official spec for instrumentation, collector setup, exporter configuration | ~20 min read |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 25, 2026 | Created as Concept 25. Covered three pillars (logs, metrics, traces), architectural topology showing observability as parallel layer, MDC for correlation, OpenTelemetry standard, SLI/SLO/SLA foundation, code examples with Micrometer + Jaeger. |
