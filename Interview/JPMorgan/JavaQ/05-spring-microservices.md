# JPMorgan — Spring Boot & Microservices Questions

> `⭐⭐⭐` = asked frequently | `⭐⭐` = in technical rounds | `⭐` = senior/lead roles
> **Context:** JPMC's stack is Spring Boot + Kafka + MySQL/MongoDB. Questions are always anchored in production scenarios.

---

## 🔴 Tier 1 — Asked Across Rounds

### Q1 `⭐⭐⭐` — How does Spring Autowiring work?

> `@Autowired` tells Spring to inject a dependency at the injection point. Spring's IoC (Inversion of Control) container looks in its `ApplicationContext` for a bean matching the required type. Three injection styles:

```java
// 1. Constructor injection (PREFERRED — immutable, testable)
@Service
public class OrderService {
    private final PaymentService paymentService;

    @Autowired  // optional in Spring 4.3+ if only one constructor
    public OrderService(PaymentService paymentService) {
        this.paymentService = paymentService;
    }
}

// 2. Field injection (common but bad — can't inject in unit tests without Spring)
@Autowired
private PaymentService paymentService;

// 3. Setter injection (use when dependency is optional)
@Autowired
public void setPaymentService(PaymentService ps) { this.paymentService = ps; }
```

**JPMC follow-up:** *"What if two beans implement the same interface?"*
> Spring throws `NoUniqueBeanDefinitionException`. Fix with `@Qualifier("beanName")` at injection point, or mark one bean as `@Primary`.

---

### Q2 `⭐⭐⭐` — Spring Security basics

**JPMC asks this in the context of financial APIs:**

```java
// Basic security config (Spring Boot 3 / Spring Security 6)
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }
}
```

**JWT flow at JPMC:**
1. Client authenticates → Auth service returns JWT
2. JWT = header.payload.signature (Base64 encoded, signed with RS256)
3. Client sends `Authorization: Bearer <token>` on every request
4. Spring Security validates signature using the public key, extracts roles from claims
5. No session on server — stateless

---

### Q3 `⭐⭐⭐` — Microservices: Monolith to Microservices (System Design Round question)

> This was directly asked as a Round 2 system design: *"Convert a monolithic architecture into microservices."*

**Core decomposition principle:**
- Split by **domain / bounded context** (DDD), not by technical layer
- Each service owns its data — no shared database
- Communication: sync (REST/gRPC) for real-time, async (Kafka) for eventual consistency

```
Monolith:
  [OrderService + UserService + PaymentService + InventoryService in one JAR]
  └── Single DB

After decomposition:
  [Order Service] ──REST──▶ [Payment Service]
       │                          │
       └──Kafka event──▶ [Inventory Service]  (eventual consistency)
       └──REST──▶ [User Service]

  Each service → own DB (order-db, payment-db, inventory-db)
  API Gateway handles routing + auth
```

**Common problems + JPMC expected answers:**
| Problem | Solution |
|---|---|
| Service discovery | Spring Cloud Eureka or Kubernetes DNS |
| Config across services | Spring Cloud Config Server |
| Cross-service auth | JWT passed in header; each service validates |
| Distributed transaction | Saga pattern (choreography via Kafka events) |
| Circuit breaking | Resilience4j `@CircuitBreaker` |
| Observability | Distributed tracing (Zipkin/Jaeger) + correlation ID |

---

### Q4 `⭐⭐` — Dynamic config reload without restart (`@RefreshScope`)

> Directly asked at JPMC: *"How to read values dynamically from a Config Server without restart?"*

```java
// application.yml
spring:
  config:
    import: "configserver:http://config-server:8888"

// @RefreshScope works by creating a proxy around the bean.
// When /actuator/refresh is called, Spring destroys the old bean instance and creates a fresh one
// — re-reading @Value fields from the Config Server. The proxy hands all calls to the new instance.
// Without @RefreshScope, @Value is injected only once at startup and stays stale forever.
@RefreshScope
@RestController
public class FeatureController {

    @Value("${feature.new-ui.enabled:false}")
    private boolean newUiEnabled;  // will be re-injected on each refresh

    @GetMapping("/status")
    public String status() {
        return "new-ui: " + newUiEnabled;
    }
}

// Trigger refresh — two ways:
// (1) POST /actuator/refresh on each individual instance (works but not scalable across 50 pods)
// (2) Spring Cloud Bus: refresh event goes to Kafka/RabbitMQ → all instances consume it → all refresh simultaneously
```

---

### Q5 `⭐⭐` — Kafka in a JPMC-style system

JPMC uses Kafka heavily. Expected at SDE-2/SDE-3 level:

```java
// Producer
@Service
public class OrderProducer {
    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

    public void publishOrderCreated(Order order) {
        OrderEvent event = new OrderEvent(order.getId(), "ORDER_CREATED", ...);
        kafkaTemplate.send("order-events", order.getId(), event);
        // key = order.getId() → same order's events always go to same partition → ordered
    }
}

// Consumer
@Service
public class PaymentConsumer {
    // groupId = "payment-service": all instances of PaymentConsumer sharing this group ID
    // collectively consume the topic — Kafka assigns each partition to exactly ONE instance.
    // Two separate services (payment-service, inventory-service) use different group IDs
    // so each gets ALL messages independently.
    @KafkaListener(topics = "order-events", groupId = "payment-service")
    public void handleOrderEvent(OrderEvent event, Acknowledgment ack) {
        try {
            processPayment(event);
            // ack.acknowledge() commits the offset to Kafka — tells the broker
            // "I have successfully processed up to this message; on restart, start from the next one"
            ack.acknowledge();  // manual commit after processing
        } catch (RetryableException e) {
            // don't ack → offset is NOT committed → Kafka redelivers this message on next poll
        } catch (NonRetryableException e) {
            ack.acknowledge();  // ack to advance the offset (otherwise stuck forever)
            sendToDeadLetter(event); // route to DLT for manual inspection / replay
        }
    }
}
```

**JPMC Kafka probe questions:**
- *"How do you ensure a message is processed exactly once?"* → Idempotent producer (`enable.idempotence=true`) + transactional consumer + idempotency key in DB
- *"What is a consumer group?"* → Multiple instances share partitions — one partition per instance at any time. Scale consumers = scale partitions.
- *"What happens if consumer is down for 2 hours?"* → Kafka retains messages per `retention.ms`; consumer resumes from last committed offset. Lag = unprocessed messages.

---

### Q6 `⭐⭐` — REST API design: idempotency + pagination

**Idempotency (critical in financial APIs):**
```
// Client sends same request twice (network retry):
POST /v1/payments
X-Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000

// Server stores key in DB:
// First request: process payment, store key → response 201
// Duplicate request: key already exists → return cached 201 response, don't re-charge
```

**Cursor-based pagination (not offset):**
```
GET /v1/transactions?cursor=eyJpZCI6MTIzfQ&limit=20

// Response
{
  "data": [...],
  "next_cursor": "eyJpZCI6MTQzfQ",  // opaque, encodes last item's sort key
  "has_more": true
}
```
> Offset pagination (`LIMIT 20 OFFSET 10000`) is O(N) on DB — scans 10,020 rows to return 20. Cursor-based pagination is O(log N) via index seek.

---

### Q7 `⭐` — Circuit Breaker (Resilience4j)

```java
@Service
public class ExternalApiClient {

    @CircuitBreaker(name = "externalApi", fallbackMethod = "fallback")
    @Retry(name = "externalApi")
    @TimeLimiter(name = "externalApi")
    public String callExternalApi(String id) {
        return restTemplate.getForObject("/api/" + id, String.class);
    }

    public String fallback(String id, Exception ex) {
        log.warn("Circuit open for {}, using fallback", id);
        return "cached-default-response";
    }
}
```

```yaml
# application.yml
resilience4j:
  circuitbreaker:
    instances:
      externalApi:
        slidingWindowSize: 10
        failureRateThreshold: 50     # open if 50%+ of last 10 calls fail
        waitDurationInOpenState: 10s  # how long to stay OPEN before trying HALF-OPEN
```

**States:** CLOSED (normal) → OPEN (reject all, return fallback) → HALF-OPEN (probe one request) → CLOSED or back to OPEN.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Aug 10, 2026 | File created from 2024–2026 JPMC interview reports. |
