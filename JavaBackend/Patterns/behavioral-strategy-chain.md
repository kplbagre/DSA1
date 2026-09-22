# 🧩 Strategy + Chain of Responsibility — Pattern

> **Strategy:** swap algorithms at runtime without changing the client. **Chain of Responsibility:** pass a request along a chain of handlers — each handler decides whether to handle it or pass it on.

---

## 🎯 The Problem

```java
// Without Strategy — hardcoded algorithm:
public double calculateDiscount(Order order) {
    if (order.getType().equals("VIP")) {
        return order.getTotal() * 0.20;
    } else if (order.getType().equals("REGULAR")) {
        return order.getTotal() * 0.05;
    } else if (order.getType().equals("EMPLOYEE")) {
        return order.getTotal() * 0.30;
    }
    return 0;
}
// Adding a new discount type = modifying this method. Violates Open/Closed Principle.

// Without Chain — hardcoded validation:
public void validateRequest(Request req) {
    validateAuth(req);       // must pass
    validateRateLimit(req);  // must pass
    validatePayload(req);    // must pass
    processRequest(req);
}
// Adding a new validation step = modifying this method. Steps can't be reordered or skipped.
```

---

## 🔧 Strategy

```java
// Define the strategy interface:
@FunctionalInterface   // one method → can use lambdas
public interface DiscountStrategy {
    double calculateDiscount(Order order);
}

// Concrete strategies:
public class VipDiscount implements DiscountStrategy {
    public double calculateDiscount(Order order) {
        return order.getTotal() * 0.20;
    }
}

public class EmployeeDiscount implements DiscountStrategy {
    public double calculateDiscount(Order order) {
        return order.getTotal() * 0.30;
    }
}

// Context — uses the strategy:
public class OrderService {
    private final DiscountStrategy discountStrategy;

    public OrderService(DiscountStrategy discountStrategy) {
        this.discountStrategy = discountStrategy;   // injected — swappable
    }

    public double finalPrice(Order order) {
        double discount = discountStrategy.calculateDiscount(order);
        return order.getTotal() - discount;
    }
}

// Usage — strategy selected at runtime:
DiscountStrategy strategy = switch (customer.getType()) {
    case "VIP" -> new VipDiscount();
    case "EMPLOYEE" -> new EmployeeDiscount();
    default -> order -> order.getTotal() * 0.05;   // lambda — inline strategy
};
OrderService service = new OrderService(strategy);

// Spring: strategies are beans, injected by qualifier or profile
@Bean @Qualifier("vip")
public DiscountStrategy vipDiscount() { return new VipDiscount(); }
```

**Where you see Strategy:**
- `Comparator` — sorting strategy passed to `Collections.sort()`, `Stream.sorted()`
- `HandlerMapping` in Spring MVC — different strategies for URL-to-handler mapping
- `AuthenticationProvider` in Spring Security — different auth strategies (JWT, LDAP, OAuth)
- `RetryPolicy` in Resilience4j — configurable retry strategy

---

## 🔧 Chain of Responsibility

```java
// Each handler decides: handle the request, or pass to the next handler.
public abstract class RequestHandler {
    private RequestHandler next;

    public RequestHandler setNext(RequestHandler next) {
        this.next = next;
        return next;   // fluent chaining
    }

    public void handle(Request request) {
        if (canHandle(request)) {
            doHandle(request);
        } else if (next != null) {
            next.handle(request);   // pass to next in chain
        } else {
            throw new UnhandledRequestException("No handler for: " + request);
        }
    }

    protected abstract boolean canHandle(Request request);
    protected abstract void doHandle(Request request);
}

// Concrete handlers:
public class AuthHandler extends RequestHandler {
    protected boolean canHandle(Request req) { return true; }   // always runs
    protected void doHandle(Request req) {
        if (!isAuthenticated(req)) {
            throw new UnauthorizedException();
        }
        if (next != null) { next.handle(req); }   // pass to next
    }
}

public class RateLimitHandler extends RequestHandler {
    protected boolean canHandle(Request req) { return true; }
    protected void doHandle(Request req) {
        if (isRateLimited(req)) {
            throw new TooManyRequestsException();
        }
        if (next != null) { next.handle(req); }
    }
}

// Build the chain:
RequestHandler chain = new AuthHandler();
chain.setNext(new RateLimitHandler())
     .setNext(new ValidationHandler())
     .setNext(new BusinessLogicHandler());

chain.handle(request);   // flows through: auth → rate limit → validation → logic
```

**Where you see Chain of Responsibility:**
- **Spring Security filter chain** — each filter checks one thing, passes to the next
- **Servlet filter chain** — `FilterChain.doFilter()` passes to the next filter
- **Java exception handling** — exception propagates up the call stack until a catch block handles it
- **Logging framework levels** — DEBUG → INFO → WARN → ERROR, each level decides if it handles

---

## 🧭 Strategy vs Chain

| Aspect | Strategy | Chain of Responsibility |
|---|---|---|
| **How many handle?** | Exactly ONE (selected at setup) | Each handler decides — one or more may handle |
| **Selection** | Client chooses strategy | Handlers self-select at runtime |
| **Structure** | One strategy object | Linked chain of handlers |
| **Use case** | Swap algorithms | Pipeline of processing steps |

---

## 🎙️ Say It in 60 Seconds

> Strategy swaps algorithms at runtime — define an interface, implement N strategies, inject the right one. `Comparator` is the canonical example: pass `Comparator.comparing(String::length)` or `Comparator.naturalOrder()` — different sort strategy, same API. Chain of Responsibility builds a pipeline — each handler processes the request and passes it to the next. Spring Security's filter chain is the canonical example: request passes through AuthenticationFilter → AuthorizationFilter → ExceptionFilter, each handling one concern. Strategy = one handler, selected by the client. Chain = multiple handlers, self-selecting.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Sep 2026 | Note #30 (Phase 5). Strategy with functional interface, Chain of Responsibility with filter chain mapping, Spring examples for both. |
