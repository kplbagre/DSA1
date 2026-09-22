# 🧩 Factory + Abstract Factory — Pattern

> **When to use:** Object creation logic is complex, depends on runtime conditions, or you want to decouple the caller from the concrete class. The caller says "give me a thing" without knowing which class is instantiated.

---

## 🎯 The Problem

```java
// Direct instantiation couples the caller to the concrete type:
Notification notification = new EmailNotification(user, message);
// If you add SMSNotification, PushNotification → every call site changes.
// If creation logic is complex (config lookup, validation) → repeated everywhere.
```

---

## 🧠 Why It Exists

Factory encapsulates the `new` keyword. The caller asks for an object by specification (type, config) and receives it — without knowing the concrete class. This enables: adding new types without changing callers (Open/Closed Principle), centralizing creation logic (validation, caching, pooling), and testability (mock the factory, not every constructor).

---

## 🔧 Implementations

### 1. Static Factory Method (simplest)

```java
// Not a GoF pattern — but the most common factory idiom in Java
public class Notification {

    public static Notification of(String type, User user, String message) {
        return switch (type) {
            case "email" -> new EmailNotification(user, message);
            case "sms" -> new SmsNotification(user, message);
            case "push" -> new PushNotification(user, message);
            default -> throw new IllegalArgumentException("Unknown type: " + type);
        };
    }
}

// Usage — caller doesn't know concrete classes:
Notification n = Notification.of("email", user, message);

// JDK examples:
List.of(1, 2, 3);           // factory method — returns an immutable List implementation
Optional.of(value);          // factory method — wraps value in Optional
Integer.valueOf(42);         // cached factory — returns same Integer for -128 to 127
```

### 2. Factory Method Pattern (GoF — subclass decides)

```java
// The base class defines the interface. Subclasses decide WHICH object to create.
public abstract class NotificationService {

    // Factory method — subclasses override to provide the concrete type
    protected abstract Notification createNotification(User user, String message);

    // Template method uses the factory method
    public void sendNotification(User user, String message) {
        Notification notification = createNotification(user, message);
        notification.validate();
        notification.send();
        auditLog(notification);
    }
}

public class EmailNotificationService extends NotificationService {
    @Override
    protected Notification createNotification(User user, String message) {
        return new EmailNotification(user, message);
    }
}

public class SmsNotificationService extends NotificationService {
    @Override
    protected Notification createNotification(User user, String message) {
        return new SmsNotification(user, message);
    }
}
```

### 3. Abstract Factory (family of related objects)

```java
// Creates FAMILIES of related objects — ensures consistency across the family.
public interface UIFactory {
    Button createButton();
    TextField createTextField();
    Dropdown createDropdown();
}

public class WalmartUIFactory implements UIFactory {
    public Button createButton() { return new WalmartButton(); }
    public TextField createTextField() { return new WalmartTextField(); }
    public Dropdown createDropdown() { return new WalmartDropdown(); }
}

public class SamsClubUIFactory implements UIFactory {
    public Button createButton() { return new SamsButton(); }
    public TextField createTextField() { return new SamsTextField(); }
    public Dropdown createDropdown() { return new SamsDropdown(); }
}

// Usage — the factory guarantees all components match the same theme:
UIFactory factory = getFactoryForBrand(brand);   // returns WalmartUIFactory or SamsClubUIFactory
Button button = factory.createButton();           // guaranteed to be the right brand
```

---

## 🏢 Where You See It

| Framework | Factory usage |
|---|---|
| **Spring IoC** | `ApplicationContext` is a factory — `getBean(OrderService.class)` returns the wired instance. You never call `new OrderService()`. |
| **Spring `BeanFactory`** | The core factory interface — `ApplicationContext` extends it. |
| **JDBC** | `DriverManager.getConnection(url)` — factory method, returns Connection without exposing the driver class. |
| **Java Collections** | `List.of()`, `Map.of()`, `Collections.unmodifiableList()` — static factory methods. |
| **SLF4J** | `LoggerFactory.getLogger(MyClass.class)` — returns the logger implementation (Logback, Log4j) without coupling to it. |

---

## ⚠️ Gotchas

- **Static factory method advantages over constructors:** can have descriptive names (`of`, `from`, `create`, `valueOf`), can return cached instances (`Integer.valueOf`), can return subtypes. Joshua Bloch's Effective Java Item 1.
- **Don't overuse:** if there's only one implementation and no complex creation logic, `new MyClass()` is fine. Factory adds a layer of indirection — justify it.
- **In Spring:** you rarely write explicit factories because Spring IS the factory. `@Bean` methods are factory methods. `@Configuration` classes are factories.

---

## 🎙️ Say It in 60 Seconds

> Factory encapsulates `new`. Three flavors: static factory method (`List.of()`, `Optional.of()` — simplest, most common), factory method pattern (subclass overrides `createX()` — GoF, used in template method), abstract factory (creates families of related objects — ensures consistency across a theme/brand). Spring's `ApplicationContext` is the ultimate factory — `getBean()` returns fully wired objects without `new`. Use factory when creation logic is complex, types vary at runtime, or you want callers decoupled from concrete classes.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Sep 2026 | Note #28 (Phase 5). Static factory method, Factory Method (GoF), Abstract Factory, Spring as factory, JDK examples. |
