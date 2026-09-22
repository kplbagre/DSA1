# ☕ Testing Strategy — Deep Dive

> After this note you can explain the test pyramid, choose between `@SpringBootTest` and slice tests (`@WebMvcTest`, `@DataJpaTest`), write a Mockito verify-behavior-not-implementation test, set up Testcontainers for integration tests with a real PostgreSQL, and articulate WHY a test exists — not just HOW to write one.

---

## 🎯 The Problem This Solves

A service has 200 endpoints. Every code change requires manual testing — deploy, click through, eyeball the response. A developer changes a utility method and breaks 3 downstream services — discovered in production, 2 hours later. Another developer writes 500 unit tests that all mock everything — the tests pass but the real service crashes because Hibernate generates different SQL than the mocked repository assumed.

Testing is not about coverage numbers. It's about **confidence**. The right test at the right layer catches the bug BEFORE production, at the LOWEST cost. The wrong test at the wrong layer gives false confidence (passes when code is broken) or slows development (runs 10 minutes for every change).

---

## 📖 Terminology

| Term | Definition |
|---|---|
| **Unit test** | Tests a single class/method in isolation. Dependencies are mocked. Fast (milliseconds). No Spring context, no database, no network. |
| **Integration test** | Tests multiple components working together — typically your code + a real database or messaging system. Slower (seconds). May start Spring context. |
| **End-to-end (E2E) test** | Tests the entire system from the entry point (HTTP request) through all layers to the response. Slowest. Catches wiring issues but expensive to maintain. |
| **Test pyramid** | Strategy: many unit tests (fast, cheap), fewer integration tests (medium), very few E2E tests (slow, expensive). Wide base, narrow top. |
| **Slice test** | A Spring Boot test that loads ONLY the Spring beans for one layer. `@WebMvcTest` = controller layer only. `@DataJpaTest` = JPA layer only. Faster than `@SpringBootTest`. |
| **Mock** | A fake object that records interactions. You verify that the code under test called the mock's methods with the right arguments. Does NOT execute real logic. |
| **Stub** | A fake object that returns pre-configured values. You set up `when(mock.findById(42)).thenReturn(user)`. The stub provides data but you don't verify calls on it. |
| **SUT (System Under Test)** | The class or method being tested. Everything else is either a real collaborator (integration) or a mock/stub (unit). |
| **Testcontainers** | A Java library that runs real databases, message brokers, etc. in Docker containers during tests. Provides true integration testing without mocking the infrastructure. |

---

## 🧠 Mental Model

Think of testing layers as **security checkpoints at an airport**:
- **Unit tests** = metal detector at the gate. Fast, checks one thing (does this method return the right value?). 500 passengers through in an hour.
- **Integration tests** = bag X-ray. Slower, checks interactions (does the service correctly query the DB and map the result?). 100 bags per hour.
- **E2E tests** = full body scan. Slowest, checks everything (does the HTTP request travel through auth → controller → service → DB → response correctly?). 20 people per hour.

You want MANY metal detectors (unit tests), SOME X-rays (integration tests), and FEW body scans (E2E). If you invert the pyramid — many E2E, few unit tests — your test suite takes 30 minutes to run, developers stop running tests, and CI becomes a bottleneck.

> If you can say "test pyramid: many unit (fast, isolated), fewer integration (real DB via Testcontainers), few E2E; @WebMvcTest for controllers, @DataJpaTest for repositories, @SpringBootTest for full context; mock behavior not implementation; Testcontainers for real infrastructure in tests" without notes, you have testing strategy.

---

## 🎨 Visual — Test Pyramid

```
          ┌─────────┐
          │  E2E    │  Few (5-10). Slow (seconds-minutes).
          │  Tests  │  Full stack: HTTP → controller → service → DB → response.
          ├─────────┤  Catches: wiring, config, serialization bugs.
          │         │  Tools: @SpringBootTest + TestRestTemplate, Testcontainers.
         ┌┤ Integr. ├┐
         │ │  Tests  │ │  Some (50-100). Medium (seconds).
         │ ├─────────┤ │  Real DB/Kafka via Testcontainers. Spring slices.
         │ │         │ │  Catches: SQL errors, JPA mapping, transaction bugs.
        ┌┤ │         │ ├┐ Tools: @DataJpaTest, @WebMvcTest, Testcontainers.
        │ │         │ │
       ┌┤   Unit    ├┐│  Many (500+). Fast (milliseconds).
       │ │  Tests   │ ││  One class, mocked dependencies. No Spring.
       │ │          │ ││  Catches: logic errors, edge cases, null handling.
       │ └──────────┘ ││  Tools: JUnit 5, Mockito, AssertJ.
       └──────────────┘│
                       │
  FAST ◄─────────────── ──────────────► SLOW
  MANY ◄─────────────── ──────────────► FEW
  CHEAP ◄────────────── ──────────────► EXPENSIVE

KEY INVARIANT:
   Each layer catches different bugs. Unit tests can't catch SQL errors.
   Integration tests can't run 500 cases efficiently. You need BOTH.
   The pyramid shape optimizes: maximum confidence per minute of test runtime.
```

---

## 🪜 Build-up

---

### Level 1 — The naive approach (and why it fails)

```java
// ❌ Anti-pattern 1: test everything with @SpringBootTest
@SpringBootTest   // loads ENTIRE Spring context — every bean, every config
class OrderServiceTest {
    @Autowired OrderService orderService;

    @Test
    void testCreateOrder() {
        orderService.create(new Order(...));
        // Test takes 15 seconds to start (Spring context + DB init)
        // 200 tests × 15s = 50 minutes to run the test suite
        // Developer stops running tests locally → pushes untested code
    }
}

// ❌ Anti-pattern 2: mock everything — tests pass, production crashes
@Test
void testCreateOrder() {
    when(orderRepository.save(any())).thenReturn(savedOrder);
    when(inventoryClient.reserve(any())).thenReturn(success);
    when(paymentClient.charge(any())).thenReturn(receipt);
    // Test passes. But in production:
    // - Hibernate generates different SQL than assumed
    // - inventoryClient returns a different JSON structure
    // - paymentClient has a 500ms timeout the test never checked
    // The test verified the mocks work, not the code.
}

// ❌ Anti-pattern 3: testing implementation, not behavior
@Test
void testCreateOrder() {
    orderService.create(order);
    verify(orderRepository).save(any(Order.class));           // fragile
    verify(eventPublisher).publishEvent(any(OrderEvent.class)); // fragile
    verify(auditLogger).log(any(String.class));               // fragile
    // If service is refactored to use a batch save → test breaks
    // Even though the BEHAVIOR (order is persisted + event published) is unchanged
    // You're testing HOW it works, not WHAT it does.
}
```

---

### Level 2 — The real mechanism

#### 2.1 — JUnit 5 — The foundation

```java
// Tier 1 — Demo: JUnit 5 basics
class OrderValidatorTest {

    private OrderValidator validator;

    @BeforeEach
    void setUp() {
        validator = new OrderValidator();   // fresh instance per test — isolation
    }

    @Test
    @DisplayName("rejects order with zero quantity")
    void rejectsZeroQuantity() {
        Order order = new Order("SKU-1", 0, 9.99);

        assertThatThrownBy(() -> validator.validate(order))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("quantity must be positive");
    }

    @Test
    @DisplayName("accepts valid order")
    void acceptsValidOrder() {
        Order order = new Order("SKU-1", 3, 9.99);

        assertThatCode(() -> validator.validate(order))
            .doesNotThrowAnyException();
    }

    // Parameterized test — one test method, multiple inputs
    @ParameterizedTest
    @ValueSource(ints = {-1, 0, -100})
    @DisplayName("rejects non-positive quantities")
    void rejectsNonPositiveQuantities(int quantity) {
        Order order = new Order("SKU-1", quantity, 9.99);

        assertThatThrownBy(() -> validator.validate(order))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // Parameterized with CSV
    @ParameterizedTest
    @CsvSource({
        "SKU-1, 1, 9.99, true",
        "SKU-1, 0, 9.99, false",
        "'', 1, 9.99, false"
    })
    void validatesOrder(String sku, int qty, double price, boolean expected) {
        Order order = new Order(sku, qty, price);
        assertThat(validator.isValid(order)).isEqualTo(expected);
    }

    // Nested test class — groups related tests
    @Nested
    @DisplayName("discount calculation")
    class DiscountTests {
        @Test
        void appliesVipDiscount() { ... }

        @Test
        void noDiscountForRegular() { ... }
    }
}
```

**JUnit 5 lifecycle:**

```
  @BeforeAll (static, once per class)
       ↓
  ┌─── @BeforeEach (before EACH test method)
  │    @Test method 1
  └─── @AfterEach (after EACH test method)
  ┌─── @BeforeEach
  │    @Test method 2
  └─── @AfterEach
       ↓
  @AfterAll (static, once per class)

  Each @Test gets a FRESH instance of the test class (default).
  This prevents state leaking between tests.
```

#### 2.2 — Mockito — Mock behavior, not implementation

```java
// Tier 2 — Production: service test with Mockito
@ExtendWith(MockitoExtension.class)   // JUnit 5 + Mockito integration
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentService paymentService;

    @InjectMocks   // creates OrderService with mocks injected via constructor
    private OrderService orderService;

    @Test
    @DisplayName("places order and charges payment")
    void placesOrderSuccessfully() {
        // Arrange — set up stubs
        Order order = new Order("SKU-1", 2, 29.99);
        when(orderRepository.save(any(Order.class)))
            .thenAnswer(invocation -> {
                Order saved = invocation.getArgument(0);
                saved.setId(42L);   // simulate DB-generated ID
                return saved;
            });
        when(paymentService.charge(anyDouble()))
            .thenReturn(new PaymentReceipt("TXN-123"));

        // Act
        OrderResult result = orderService.placeOrder(order);

        // Assert — verify BEHAVIOR, not implementation details
        assertThat(result.getOrderId()).isEqualTo(42L);
        assertThat(result.getPaymentRef()).isEqualTo("TXN-123");

        // Verify critical interactions (sparingly — only for side effects)
        verify(paymentService).charge(59.98);   // 2 × 29.99
        // DON'T verify: orderRepository.save() — it's obvious from the result
    }

    @Test
    @DisplayName("rolls back on payment failure")
    void rollsBackOnPaymentFailure() {
        when(orderRepository.save(any())).thenReturn(new Order());
        when(paymentService.charge(anyDouble()))
            .thenThrow(new PaymentDeclinedException("Insufficient funds"));

        assertThatThrownBy(() -> orderService.placeOrder(new Order("SKU-1", 1, 100.0)))
            .isInstanceOf(OrderProcessingException.class)
            .hasMessageContaining("Payment failed");

        // Verify: order was NOT left in a committed state
        verify(orderRepository, never()).updateStatus(anyLong(), eq("CONFIRMED"));
    }
}
```

**Mockito rules:**

```
  ✅ DO: verify SIDE EFFECTS that matter (email sent, event published, payment charged)
  ❌ DON'T: verify every internal method call (save, find, log) — that's testing implementation

  ✅ DO: stub methods that return values the SUT depends on
  ❌ DON'T: stub methods just to avoid NullPointerException — that's a design smell

  ✅ DO: use argument matchers (any(), eq(), argThat()) for flexible matching
  ❌ DON'T: match exact objects when only one field matters — use argThat(o -> o.getId() == 42)
```

#### 2.3 — Spring Boot Slice Tests

```java
// @WebMvcTest — loads ONLY controller layer (controller + MockMvc + Jackson)
// NO service beans, NO repository beans, NO database
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean   // Spring-managed mock (replaces the real bean in the context)
    private OrderService orderService;

    @Test
    void getOrder_returnsOrder() throws Exception {
        when(orderService.findById(42L))
            .thenReturn(Optional.of(new Order(42L, "Alice", "PENDING")));

        mockMvc.perform(get("/api/orders/42"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.customerName").value("Alice"))
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void getOrder_returns404_whenNotFound() throws Exception {
        when(orderService.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/orders/99"))
            .andExpect(status().isNotFound());
    }

    @Test
    void createOrder_validates_requestBody() throws Exception {
        String invalidJson = """
            {"customerName": "", "quantity": -1}
            """;

        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
            .andExpect(status().isBadRequest());
    }
}
// Starts in ~2 seconds (only controller layer loaded)

// @DataJpaTest — loads ONLY JPA layer (entities, repositories, embedded DB)
// NO controllers, NO services, NO web layer
@DataJpaTest
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findByStatus_returnsMatchingOrders() {
        // Arrange — insert test data via TestEntityManager
        entityManager.persist(new Order("Alice", "PENDING"));
        entityManager.persist(new Order("Bob", "SHIPPED"));
        entityManager.persist(new Order("Charlie", "PENDING"));
        entityManager.flush();

        // Act
        List<Order> pending = orderRepository.findByStatus("PENDING");

        // Assert
        assertThat(pending).hasSize(2)
            .extracting(Order::getCustomerName)
            .containsExactlyInAnyOrder("Alice", "Charlie");
    }
}
// Uses embedded H2 by default. Or override with Testcontainers (see below).
```

**Slice test decision table:**

| Slice annotation | What it loads | What it mocks | Use when |
|---|---|---|---|
| `@WebMvcTest` | Controller + MockMvc + Jackson + validation | Service layer (`@MockBean`) | Testing request mapping, JSON serialization, validation, error handling |
| `@DataJpaTest` | Entities + Repositories + embedded DB | Nothing (real DB) | Testing custom queries, JPA mappings, derived query methods |
| `@JsonTest` | Jackson ObjectMapper | Everything else | Testing JSON serialization/deserialization only |
| `@SpringBootTest` | ENTIRE context | Nothing (or selectively with `@MockBean`) | Full integration tests, wiring tests |

#### 2.4 — Testcontainers — Real infrastructure in tests

```java
// Tier 2 — Production: integration test with real PostgreSQL
@SpringBootTest
@Testcontainers   // manage Docker container lifecycle
class OrderIntegrationTest {

    @Container   // starts a real PostgreSQL in Docker
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource   // point Spring's DataSource to the container
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void createOrder_persistsToRealDatabase() {
        OrderResult result = orderService.placeOrder(new Order("Alice", "SKU-1", 2));

        assertThat(result.getOrderId()).isNotNull();

        // Verify directly in the database
        Optional<Order> fromDb = orderRepository.findById(result.getOrderId());
        assertThat(fromDb).isPresent();
        assertThat(fromDb.get().getCustomerName()).isEqualTo("Alice");
    }
}
// Starts a REAL PostgreSQL container. Tests run against REAL SQL.
// Catches: Hibernate SQL generation bugs, DB constraint violations,
// transaction behavior — things H2 can't catch because H2's SQL dialect differs.
```

**When to use Testcontainers vs embedded H2:**

| Aspect | Embedded H2 | Testcontainers (PostgreSQL) |
|---|---|---|
| Speed | Fast (~1s startup) | Slower (~5s first container, reused after) |
| SQL dialect | H2 SQL (differs from PostgreSQL) | Real PostgreSQL SQL |
| Catches | Basic JPA mapping, simple queries | Dialect-specific bugs, constraint violations, real query plans |
| Setup | Zero config (Spring Boot default) | Docker required, `@Testcontainers` + `@Container` |
| Use when | Unit-testing repository methods | Validating SQL before production, CI pipeline |

#### 2.5 — WireMock — Mock external APIs

```java
// Tier 2 — Production: mock external payment API
@SpringBootTest
@WireMockTest(httpPort = 8089)   // starts WireMock server on port 8089
class PaymentIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Test
    void handlesPaymentSuccess() {
        // Stub the external payment API response
        stubFor(post(urlEqualTo("/api/payments"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"transactionId": "TXN-123", "status": "SUCCESS"}
                    """)));

        OrderResult result = orderService.placeOrder(new Order("Alice", "SKU-1", 2));
        assertThat(result.getPaymentRef()).isEqualTo("TXN-123");
    }

    @Test
    void handlesPaymentTimeout() {
        stubFor(post(urlEqualTo("/api/payments"))
            .willReturn(aResponse()
                .withStatus(200)
                .withFixedDelay(5000)));   // 5 second delay → triggers timeout

        assertThatThrownBy(() -> orderService.placeOrder(new Order("Alice", "SKU-1", 2)))
            .isInstanceOf(PaymentTimeoutException.class);
    }

    @Test
    void handlesPayment500() {
        stubFor(post(urlEqualTo("/api/payments"))
            .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> orderService.placeOrder(new Order("Alice", "SKU-1", 2)))
            .isInstanceOf(PaymentServiceException.class);
    }
}
```

---

### Level 3 — The subtleties

#### 3.1 — What makes a good test (staff-level thinking)

```
  A GOOD TEST answers: "If this test passes, can I deploy with confidence?"

  5 PROPERTIES OF A GOOD TEST:

  1. FAST — runs in milliseconds (unit) or seconds (integration). Not minutes.
     If the suite takes 30 min, developers stop running it.

  2. ISOLATED — tests don't depend on each other or shared state.
     Test A passing should not require Test B to run first.
     Each test sets up its own data and cleans up after.

  3. DETERMINISTIC — same code → same result. Every time.
     No flaky tests. No "works on my machine." No time-dependent logic
     (mock the clock, don't use System.currentTimeMillis()).

  4. BEHAVIOR-FOCUSED — tests WHAT the code does, not HOW it does it.
     "placeOrder saves the order and charges payment" (behavior)
     NOT "placeOrder calls repository.save then calls paymentService.charge" (implementation)
     If you refactor internals without changing behavior → tests should still pass.

  5. READABLE — the test IS the documentation.
     A new developer reads the test and understands the requirement.
     Use @DisplayName. Use clear variable names. Follow Arrange-Act-Assert.
```

#### 3.2 — Test data patterns

```java
// ❌ Anti-pattern: constructing test data inline (verbose, repeated)
@Test
void test1() {
    Order order = new Order(1L, "Alice", "alice@test.com", "SKU-1", 2, 29.99, "PENDING", LocalDate.now(), false, 0.0);
    // 10 parameters — what's what?
}

// ✅ Builder pattern for test data (reusable, self-documenting)
public class TestOrder {
    public static Order.Builder aValidOrder() {
        return Order.builder()
            .id(1L)
            .customerName("Alice")
            .email("alice@test.com")
            .sku("SKU-1")
            .quantity(2)
            .price(29.99)
            .status("PENDING")
            .orderDate(LocalDate.of(2026, 9, 22))
            .expedited(false)
            .discount(0.0);
    }
}

// Usage — override only what matters for THIS test:
@Test
void appliesVipDiscount() {
    Order order = TestOrder.aValidOrder().customerType("VIP").build();
    // Only customerType is relevant to this test — everything else is default
}

@Test
void rejectsExpiredOrder() {
    Order order = TestOrder.aValidOrder()
        .orderDate(LocalDate.of(2020, 1, 1))   // expired
        .build();
}
```

#### 3.3 — `@SpringBootTest` vs slice tests — the cost

```
  @SpringBootTest: loads ENTIRE context.
  - All beans created and wired
  - Database schema created
  - All auto-configuration applied
  - Startup: 5-15 seconds

  @WebMvcTest(OrderController.class): loads ONE controller.
  - Only OrderController + MockMvc + Jackson + validation
  - Services/repositories are @MockBean
  - Startup: 1-3 seconds

  For 200 controller tests:
  - @SpringBootTest: 200 tests × (context cached, but ~5s first start) + test time
  - @WebMvcTest: separate context per controller class, ~2s each, much lighter

  RULE: use the NARROWEST slice that tests what you need.
  @DataJpaTest for query logic. @WebMvcTest for HTTP handling.
  @SpringBootTest ONLY for full-stack integration tests.
```

---

## ⚠️ Common Misconceptions

| You might think... | But actually... |
|---|---|
| "100% code coverage means no bugs" | Coverage measures which LINES ran, not which BEHAVIORS are tested. 100% line coverage with no assertions = zero value. A well-chosen 70% coverage that tests critical paths + edge cases beats 100% coverage with trivial tests. |
| "`@SpringBootTest` is the right default for all tests" | It's the SLOWEST option — loads the entire context. Use slice tests (`@WebMvcTest`, `@DataJpaTest`) for layer-specific testing. Reserve `@SpringBootTest` for full integration tests. |
| "Mocking everything makes tests fast and reliable" | Over-mocking creates tests that verify mocks work, not code. If you mock the repository and the real SQL is wrong, the test passes but production crashes. Use Testcontainers for repository tests — test against real SQL. |
| "Flaky tests are normal" | Flaky tests (sometimes pass, sometimes fail) destroy trust. Developers ignore the suite. Common causes: shared test state, time-dependent logic, network calls, non-deterministic ordering. Every flaky test must be fixed or deleted. |
| "TDD means writing tests first" | TDD is a DESIGN technique, not a testing technique. Writing the test first forces you to think about the API from the caller's perspective BEFORE implementation. The test is a byproduct. The design clarity is the point. |

---

## 🐞 Production Footguns

---

> **Footgun: H2 passes, PostgreSQL fails**
> **Cost:** Bug discovered in staging/production, not in tests
>
> A repository test used the default embedded H2. The query used `ILIKE` (PostgreSQL-specific case-insensitive matching). H2 doesn't support `ILIKE` — but the test never ran the real SQL because the derived query method was mocked. In production: `org.postgresql.util.PSQLException: ERROR: syntax error at or near "ILIKE"`.

```java
// ❌ The trap: embedded H2 hides dialect differences
@DataJpaTest   // uses H2 by default
class UserRepositoryTest {
    @Test
    void findByEmailIgnoreCase() {
        // Test passes on H2 — but H2 doesn't support the same SQL as PostgreSQL
    }
}

// ✅ The fix: Testcontainers with real PostgreSQL
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {
    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:15");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", pg::getJdbcUrl);
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
    }
    // Now tests run against REAL PostgreSQL — dialect bugs caught here, not production
}
```

---

> **Footgun: Testing implementation → brittle tests after refactor**
> **Cost:** Every refactor breaks 50 tests that should still pass
>
> A team verified every internal method call with `verify()`. When they refactored the service to batch database writes (performance improvement), 50 tests broke — they all verified `repository.save()` was called N times, but the refactored code called `repository.saveAll()` once. The behavior was identical (orders persisted), but the tests tied to implementation, not behavior.

```java
// ❌ The trap: verify implementation details
verify(repository, times(3)).save(any(Order.class));   // breaks on batch refactor

// ✅ The fix: verify behavior through assertions
List<Order> saved = orderRepository.findAll();
assertThat(saved).hasSize(3);
assertThat(saved).extracting(Order::getStatus).containsOnly("CONFIRMED");
// Passes whether save() or saveAll() was used internally
```

---

## 🔗 Concept Web

| Connects to | How |
|---|---|
| `exception-hierarchy.md` | Tests verify exception paths: `assertThatThrownBy().isInstanceOf()`. Understanding checked vs unchecked determines whether the test expects a specific exception or a wrapped RuntimeException. |
| `../Spring/DeepDive/04-jpa-transactions.md` | `@DataJpaTest` tests JPA layer. `@Transactional` on tests rolls back after each test (auto-cleanup). Understanding transaction propagation explains why test data doesn't leak between tests. |
| `../Spring/DeepDive/03-spring-mvc-boot.md` | `@WebMvcTest` tests the MVC layer. Understanding DispatcherServlet → HandlerMapping → Controller flow explains what MockMvc is actually testing. `@ControllerAdvice` exception handling is tested here. |
| `completable-future.md` (in `StreamsFunctional/`) | Testing async code: `CompletableFuture.join()` in tests to block until async result. `@Async` methods need special test setup (mock the executor or use `@SpyBean`). |

---

## 🎙️ Interview Deep Questions

**Q1. Explain the test pyramid. Why not just write E2E tests for everything?**

> The test pyramid says: many unit tests (fast, cheap, isolated), fewer integration tests (real infrastructure, slower), very few E2E tests (full stack, slowest). If you invert it — many E2E, few unit tests — you get a 30-minute test suite that developers stop running locally, flaky tests from network/timing issues, and slow feedback on simple logic bugs that a unit test would catch in milliseconds. E2E tests are essential for catching wiring and config issues, but they're expensive to write, slow to run, and fragile to maintain. Unit tests catch 80% of bugs at 1% of the cost. The pyramid optimizes: maximum confidence per minute of test runtime.

**Q2. When do you use `@WebMvcTest` vs `@SpringBootTest` vs `@DataJpaTest`?**

> `@WebMvcTest(Controller.class)`: loads ONLY the controller, MockMvc, Jackson, and validation beans. Service layer is `@MockBean`. Tests: request mapping, JSON serialization/deserialization, validation errors, `@ControllerAdvice` error handling. Starts in 1-3 seconds. `@DataJpaTest`: loads ONLY the JPA layer — entities, repositories, embedded DB (or Testcontainers). Tests: custom queries, JPA mappings, derived query methods against real SQL. `@SpringBootTest`: loads the ENTIRE context. Tests: full integration — HTTP request through all layers to the database and back. Use for smoke tests and critical paths only. The rule: use the narrowest slice that covers what you're testing. Don't load 200 beans to test one controller method.

**Q3. What is the difference between mocking and stubbing? When should you use `verify()`?**

> A stub provides canned return values: `when(repo.findById(42)).thenReturn(user)`. You set it up for the test to proceed but don't verify calls to it. A mock records interactions for verification: `verify(emailService).send(eq(user.email()), any())`. Use `verify()` ONLY for important side effects — actions the SUT must perform but that don't show up in the return value (email sent, event published, payment charged). Don't verify internal implementation details (repository.save called N times) — that couples tests to implementation. If you refactor internals without changing behavior, tests should still pass. Rule: assert return values, verify side effects, ignore internal wiring.

**Q4. How do Testcontainers work and when should you use them?**

> Testcontainers starts real Docker containers (PostgreSQL, Kafka, Redis) during test execution. You annotate the test class with `@Testcontainers` and declare a `@Container` field with the image. `@DynamicPropertySource` points Spring's DataSource to the container's URL/port. Tests run against real infrastructure — real SQL dialect, real Kafka consumer behavior, real Redis commands. Use Testcontainers when: the embedded alternative (H2) has SQL dialect differences that hide bugs, you need to test Kafka consumer/producer patterns, or you need to verify transaction isolation behavior. Don't use for every test — reserve for integration tests. Unit tests should use mocks.

**Q5. How do you design a testing strategy for a new microservice?**

> Four layers: (1) Unit tests for every service class and utility — mock dependencies, test business logic and edge cases. 500+ tests, all run in < 30 seconds. (2) Repository tests with `@DataJpaTest` + Testcontainers — test every custom query against real PostgreSQL. 50-100 tests. (3) Controller tests with `@WebMvcTest` — test request mapping, validation, error handling, JSON contracts. 50-100 tests. (4) A handful of `@SpringBootTest` full-integration tests for critical user journeys — order creation, payment processing, authentication flow. 5-10 tests. CI runs all on every PR. Gate: no merge if any test fails. Coverage target: 70%+ line coverage, but measured meaningful coverage — every branch in business logic tested, not just getters/setters.

---

## 🎙️ Say It in 60 Seconds

> **Part 1 — What (10s):** Test pyramid: many unit tests (fast, isolated, mocked), fewer integration tests (real DB via Testcontainers), few E2E tests (full stack). Each layer catches different bugs. The pyramid optimizes confidence per minute of runtime.
>
> **Part 2 — How/Why (30s):** JUnit 5 + Mockito for unit tests — mock dependencies, verify behavior not implementation. `@WebMvcTest` for controller layer (MockMvc tests request mapping and JSON), `@DataJpaTest` for repository layer (test real SQL against Testcontainers PostgreSQL, not embedded H2). `@SpringBootTest` only for full-stack integration tests — it loads everything and takes seconds to start. WireMock for external API simulation (test timeout, 500, malformed response scenarios).
>
> **Part 3 — Gotcha (20s):** Two traps: H2 in tests hides PostgreSQL-specific SQL bugs — use Testcontainers with the same DB as production. And testing implementation with `verify(repository.save())` makes tests brittle — every refactor breaks tests that should still pass. Assert behavior (the order exists in the DB) not implementation (save was called once).

---

## 🧾 TL;DR

- **Test pyramid:** many unit (ms, mocked) > fewer integration (s, real DB) > few E2E (slow, full stack).
- **JUnit 5:** `@Test`, `@ParameterizedTest`, `@Nested`, `@BeforeEach`. Fresh instance per test.
- **Mockito:** `when().thenReturn()` for stubs. `verify()` ONLY for side effects. Don't verify implementation.
- **`@WebMvcTest`** = controller only. **`@DataJpaTest`** = JPA only. **`@SpringBootTest`** = everything.
- **Testcontainers** = real DB in Docker during tests. Catches SQL dialect bugs H2 misses.
- **WireMock** = mock external HTTP APIs. Test timeouts, errors, malformed responses.
- **Test behavior, not implementation.** Refactoring internals should not break tests.
- **Coverage target:** 70%+ meaningful coverage. Branches in business logic > getters/setters.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Sep 2026 | Note created as Note #37 (Phase 6, Tier 1) of the JavaBackend KB completion roadmap. Staff-level depth: test pyramid with visual, JUnit 5 lifecycle + parameterized tests, Mockito mock vs stub + verify rules, Spring slice tests (@WebMvcTest, @DataJpaTest, @JsonTest) with decision table, Testcontainers PostgreSQL setup + @DynamicPropertySource, WireMock for external APIs, test data builder pattern, 5 properties of good tests, @SpringBootTest vs slices cost analysis. Two production footguns: H2 hides PostgreSQL dialect bugs, verify-implementation breaks on refactor. |
