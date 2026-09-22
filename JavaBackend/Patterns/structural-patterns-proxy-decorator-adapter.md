# 🧩 Proxy vs Decorator vs Adapter — Pattern

> **When to use:** All three wrap an object. **Proxy** controls access (lazy load, security, transaction). **Decorator** adds behavior (logging, caching, compression). **Adapter** converts interface (make incompatible APIs work together). Same structure, different intent.

---

## 🎯 The Confusion

All three have the same shape: a wrapper class that holds a reference to the real object and delegates calls. Interviewers ask "what's the difference?" because the STRUCTURE is identical — the INTENT is what distinguishes them.

```
  Same structure:
  Wrapper implements Interface {
      private Interface delegate;
      method() { /* wrapper logic */ delegate.method(); /* more logic */ }
  }

  Different intent:
  PROXY:     controls ACCESS to the delegate (the caller may not know it's a proxy)
  DECORATOR: adds BEHAVIOR to the delegate (the caller composes decorators explicitly)
  ADAPTER:   converts INTERFACE of the delegate (makes incompatible APIs compatible)
```

---

## 🔧 Implementations

### Proxy — controls access

```java
// The caller doesn't know it's talking to a proxy.
// The proxy intercepts the call and adds control logic.

// Example: Spring @Transactional proxy
// You write:
@Service
public class OrderService {
    @Transactional
    public void placeOrder(Order order) {
        repository.save(order);
    }
}

// Spring creates a CGLIB PROXY at runtime:
// OrderService$$EnhancerBySpringCGLIB extends OrderService {
//     @Override
//     public void placeOrder(Order order) {
//         transactionManager.begin();       // proxy adds this
//         try {
//             super.placeOrder(order);       // delegates to real method
//             transactionManager.commit();   // proxy adds this
//         } catch (RuntimeException e) {
//             transactionManager.rollback(); // proxy adds this
//             throw e;
//         }
//     }
// }

// Manual proxy example — lazy initialization:
public class LazyImageProxy implements Image {
    private final String filename;
    private Image realImage;   // loaded on first use

    public LazyImageProxy(String filename) {
        this.filename = filename;
        // realImage NOT loaded yet — heavy object deferred
    }

    @Override
    public void display() {
        if (realImage == null) {
            realImage = new HighResImage(filename);   // lazy load on first call
        }
        realImage.display();
    }
}
```

**Proxy types:** virtual proxy (lazy loading), protection proxy (access control), remote proxy (RMI/gRPC stub), caching proxy.

### Decorator — adds behavior

```java
// The caller KNOWS it's composing decorators — that's the point.
// Decorators stack: base → decorator1 → decorator2 → ...

// JDK example — I/O streams:
InputStream base = new FileInputStream("data.gz");
InputStream buffered = new BufferedInputStream(base);       // adds buffering
InputStream decompressed = new GZIPInputStream(buffered);   // adds decompression
// Each decorator adds ONE behavior. They compose.

// Custom decorator:
public interface OrderProcessor {
    void process(Order order);
}

public class CoreOrderProcessor implements OrderProcessor {
    public void process(Order order) {
        // actual business logic
    }
}

public class LoggingDecorator implements OrderProcessor {
    private final OrderProcessor delegate;

    public LoggingDecorator(OrderProcessor delegate) {
        this.delegate = delegate;
    }

    @Override
    public void process(Order order) {
        log.info("Processing order: {}", order.getId());
        delegate.process(order);   // delegate to wrapped processor
        log.info("Completed order: {}", order.getId());
    }
}

public class MetricsDecorator implements OrderProcessor {
    private final OrderProcessor delegate;
    private final MeterRegistry registry;

    public MetricsDecorator(OrderProcessor delegate, MeterRegistry registry) {
        this.delegate = delegate;
        this.registry = registry;
    }

    @Override
    public void process(Order order) {
        Timer.Sample sample = Timer.start(registry);
        delegate.process(order);
        sample.stop(registry.timer("order.processing"));
    }
}

// Compose: core → logging → metrics
OrderProcessor processor = new MetricsDecorator(
    new LoggingDecorator(
        new CoreOrderProcessor()
    ),
    meterRegistry
);
```

### Adapter — converts interface

```java
// Makes an incompatible API conform to the interface the caller expects.

// Scenario: your code expects Iterator, but the library gives Enumeration (legacy)
public class EnumerationAdapter<T> implements Iterator<T> {
    private final Enumeration<T> enumeration;

    public EnumerationAdapter(Enumeration<T> enumeration) {
        this.enumeration = enumeration;
    }

    @Override
    public boolean hasNext() {
        return enumeration.hasMoreElements();   // translate method name
    }

    @Override
    public T next() {
        return enumeration.nextElement();   // translate method name
    }
}

// Real-world: Spring's HandlerAdapter
// DispatcherServlet expects HandlerAdapter interface.
// Different handler types (Controller, HttpRequestHandler, Servlet) have different APIs.
// Spring provides an Adapter for each:
//   SimpleControllerHandlerAdapter adapts Controller interface
//   HttpRequestHandlerAdapter adapts HttpRequestHandler interface
//   Each adapter translates the specific handler's API to the common HandlerAdapter interface
```

---

## 🧭 Decision Matrix

| Pattern | Intent | Caller knows? | Interface change? | Example |
|---|---|---|---|---|
| **Proxy** | Control access | No (transparent) | Same interface | Spring @Transactional, lazy loading, security checks |
| **Decorator** | Add behavior | Yes (composes explicitly) | Same interface | Java I/O streams, logging wrapper, caching wrapper |
| **Adapter** | Convert interface | Yes (explicit conversion) | Different interfaces bridged | Enumeration→Iterator, Spring HandlerAdapter |

---

## 🏢 Where You See It

| Pattern | Spring / JDK usage |
|---|---|
| **Proxy** | `@Transactional`, `@Async`, `@Cacheable` (CGLIB/JDK dynamic proxy). `java.lang.reflect.Proxy`. Hibernate lazy-loading proxies. |
| **Decorator** | `BufferedInputStream(FileInputStream)`. `Collections.synchronizedList(list)`. `Collections.unmodifiableList(list)`. Spring's `BeanPostProcessor` (wraps beans). |
| **Adapter** | `Arrays.asList(array)` (array → List). `InputStreamReader(InputStream)` (bytes → chars). Spring `HandlerAdapter`. Jackson `@JsonAdapter`. |

---

## 🎙️ Say It in 60 Seconds

> All three wrap an object. Proxy controls ACCESS — the caller doesn't know it's a proxy. Spring's @Transactional is a proxy: the CGLIB subclass intercepts calls, adds BEGIN/COMMIT/ROLLBACK, and delegates to the real method. Decorator adds BEHAVIOR — the caller composes decorators explicitly. Java I/O: `BufferedInputStream(GZIPInputStream(FileInputStream))` — each layer adds one capability. Adapter converts INTERFACE — bridges incompatible APIs. `Arrays.asList()` adapts an array to the `List` interface. Same wrapper structure, different intent: Proxy = control, Decorator = enhance, Adapter = translate.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Sep 2026 | Note #29 (Phase 5). Proxy (Spring CGLIB, lazy loading), Decorator (I/O streams, logging/metrics wrapper), Adapter (Enumeration→Iterator, Spring HandlerAdapter). Decision matrix. |
