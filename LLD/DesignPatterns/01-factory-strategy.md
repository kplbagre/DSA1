# Factory + Strategy Patterns

> **Standard followed:** `LLD/notes-standards.md`

---

## Factory Pattern

### 🎯 What Problem Does It Solve?

When your code needs to create objects but the exact type depends on a runtime condition, you end up with `if-else` or `switch` chains scattered across the codebase. Every new type means hunting down every one of those chains. Factory centralises that decision in one place — a single class whose only job is to decide which object to create.

### 🧠 Mental Model

Think of a **car manufacturing plant**. You walk up to the counter and say "I want a sedan." The factory floor figures out which assembly line to use, which parts to order, which workers to assign. You get back a fully built car. You never went near the assembly line yourself — you just specified what you wanted.

In code: the caller says `SpotFactory.create(SpotType.COMPACT)`. It gets back a `CompactSpot`. It never calls `new CompactSpot()` directly. The factory owns the `new`.

**The key insight is:** Factory decouples *what you want* from *how it gets built*. When you add a new type, you update the factory — nowhere else.

### 🔌 The Interface Contract

```java
// The common type that all created objects share
public interface ParkingSpot {

    SpotType getType();

    boolean isAvailable();

    void park(Vehicle vehicle);

    void vacate();
}
```

### ⚙️ Implementation

**Steps:**
1. **Define the common interface** that all created objects implement.
2. **Write each concrete class** implementing that interface.
3. **Write a Factory class** with a static `create()` method that maps input to concrete type.
4. **Callers use the factory** — never `new` the concrete class directly.

```java
// Step 2 — concrete implementations
public class CompactSpot implements ParkingSpot {

    private Vehicle parkedVehicle;

    @Override
    public SpotType getType() {
        return SpotType.COMPACT;
    }

    @Override
    public boolean isAvailable() {
        return parkedVehicle == null;
    }

    @Override
    public void park(Vehicle vehicle) {
        this.parkedVehicle = vehicle;
    }

    @Override
    public void vacate() {
        this.parkedVehicle = null;
    }
}

public class LargeSpot implements ParkingSpot {
    @Override
    public SpotType getType() {
        return SpotType.LARGE;
    }
    // ... same methods as CompactSpot
}
```

```java
// Step 3 — the Factory: one place, all creation decisions
public class ParkingSpotFactory {

    public static ParkingSpot create(SpotType type) {
        switch (type) {
            case COMPACT:
                return new CompactSpot();
            case LARGE:
                return new LargeSpot();
            case HANDICAPPED:
                return new HandicappedSpot();
            default:
                throw new IllegalArgumentException("Unknown spot type: " + type);
        }
    }
}
```

```java
// Step 4 — caller never uses new directly
ParkingSpot spot = ParkingSpotFactory.create(SpotType.COMPACT);
```

### 🏢 Real World Usage

- **Android** — `View.inflate(context, layoutId, parent)` is a factory. You pass a layout ID; the framework decides which `View` subclass to instantiate. You never call `new TextView()` directly from inflate.
- **JDBC / Spring DataSource** — `DriverManager.getConnection(url)` is a factory. You pass a URL; it decides which `Driver` implementation handles `jdbc:mysql` vs `jdbc:postgresql`.
- **Kafka** — `ProducerRecord` creation is often wrapped in factory methods that set default headers, serializers, and topic names based on environment config.
- **Your parking lot** — `ParkingSpotFactory.create(SpotType)` creates the right spot type. When you add EV spots, you add one enum value and one line in the factory — nothing else changes.

### 🧩 LLD Problems That Use Factory Pattern

- **Parking Lot** — Factory creates `CompactSpot`, `LargeSpot`, `HandicappedSpot`, `EVSpot` based on `SpotType` enum. Adding a new spot type = one enum value + one case in the factory. Zero changes to callers.
- **BookMyShow / Movie Ticket Booking** — Factory creates seat objects (`GoldSeat`, `SilverSeat`, `PlatinumSeat`) based on seat tier. Each type has different pricing, capacity, and amenity rules baked in.
- **Elevator System** — Factory creates `InternalRequest` (cabin button) or `ExternalRequest` (floor button) based on request origin. Centralises request construction with default priorities.
- **Vending Machine** — Factory creates the right `Item` object (Beverage, Snack, Combo) from a product code. Each item type knows its own price, stock threshold, and dispensing behaviour.
- **Rate Limiter (LLD)** — Factory creates the right limiter implementation (`TokenBucketLimiter`, `SlidingWindowLimiter`, `FixedWindowLimiter`) based on a config string. The rate limiting service calls `RateLimiterFactory.create(config)`.
- **Logger System** — Factory creates the right log handler (`FileHandler`, `ConsoleHandler`, `DatabaseHandler`, `CloudWatchHandler`) based on config. Adding a new log destination = one new class + one case in factory.
- **Cab Booking / Ride Sharing** — Factory creates ride types (`AutoRide`, `MiniRide`, `PremierRide`, `SUVRide`) based on user's selection. Each type has its own pricing model, capacity, and ETA formula.
- **Stock Exchange / Order Book** — Factory creates order types (`MarketOrder`, `LimitOrder`, `StopLossOrder`, `GoodTillCancelOrder`). Each type has different matching and expiry rules.
- **Library Management** — Factory creates library items (`Book`, `DVD`, `Magazine`, `EBook`) based on item category. Each type has different loan duration, fine rates, and renewal rules.
- **File System (mkdir, ls, cd)** — Factory creates `FileNode` or `DirectoryNode` based on the type flag. Composite pattern depends on Factory to build the tree — caller says "create a node" without specifying the subtype.
- **Meeting Room Reservation** — Factory creates `StandUpRoom`, `ConferenceRoom`, `InterviewRoom` based on meeting type. Each room type has different capacity, AV equipment, and booking constraints.
- **Splitwise** — Factory creates expense types (`OneTimeExpense`, `RecurringExpense`, `GroupExpense`) based on how the expense was entered. Each type has different split and reminder behaviour.

### 🧭 When to Use vs When NOT to Use

| Use Factory when | Do NOT use when |
|---|---|
| Object creation depends on runtime conditions | You always create the same type |
| Adding a new type should not ripple through the codebase | There's only one concrete implementation |
| Construction is complex and should be centralised | Simple `new MyClass()` is perfectly readable |
| You want to enforce the interface at the creation point | The factory would just have one `return new X()` |

**The common mistake:** Creating a factory "for future flexibility" when there's only one type today. YAGNI — factory adds indirection; earn it only when you have multiple types or complex construction.

---

## Strategy Pattern

### 🎯 What Problem Does It Solve?

You have one operation (calculate fee, sort, route, compress) but the algorithm for that operation needs to vary — based on vehicle type, user tier, time of day, or business rules that change. Without Strategy, you add `if-else` inside the method every time a new algorithm appears. With Strategy, each algorithm is its own class, swappable at runtime.

### 🧠 Mental Model

Think of **Google Maps route options**. You type in "home to office." Google shows you three buttons: Fastest, Avoid Tolls, Walking. Same start, same destination — three different algorithms. You pick one at runtime. Maps doesn't rewrite itself every time a new route type is added; it just adds a new button.

In code: `FeeStrategy` is the interface. `HourlyFeeStrategy`, `FlatRateFeeStrategy`, `WeekendFeeStrategy` are the buttons. The parking lot holds a reference to whichever strategy is currently active.

**The key insight is:** Strategy separates *what the operation does* from *how it does it*. The caller doesn't care which algorithm runs — it just calls `calculate()`.

### 🔌 The Interface Contract

```java
// The strategy interface — defines the operation, not the algorithm
public interface FeeStrategy {

    // vehicle and durationMinutes are all the context the algorithm needs
    double calculate(Vehicle vehicle, long durationMinutes);
}
```

### ⚙️ Implementation

**Steps:**
1. **Define the strategy interface** with the operation signature.
2. **Write each concrete strategy** implementing that interface.
3. **Inject the strategy** into the class that needs it (via constructor or setter).
4. **Call the interface method** — the concrete algorithm runs transparently.

```java
// Step 2 — each algorithm is its own class
public class HourlyFeeStrategy implements FeeStrategy {

    private final double ratePerHour;

    public HourlyFeeStrategy(double ratePerHour) {
        this.ratePerHour = ratePerHour;
    }

    @Override
    public double calculate(Vehicle vehicle, long durationMinutes) {
        double hours = Math.ceil(durationMinutes / 60.0);
        return hours * ratePerHour;
    }
}

public class FlatRateFeeStrategy implements FeeStrategy {

    private final double flatRate;

    public FlatRateFeeStrategy(double flatRate) {
        this.flatRate = flatRate;
    }

    @Override
    public double calculate(Vehicle vehicle, long durationMinutes) {
        return flatRate;
    }
}
```

```java
// Step 3 — inject the strategy; the parking lot doesn't know which one
public class ParkingLot {

    private final FeeStrategy feeStrategy;

    public ParkingLot(FeeStrategy feeStrategy) {
        this.feeStrategy = feeStrategy;
    }

    public double calculateFee(Vehicle vehicle, long durationMinutes) {
        // Step 4 — calls the interface; concrete algorithm is invisible here
        return feeStrategy.calculate(vehicle, durationMinutes);
    }
}
```

```java
// Usage — caller decides which strategy at construction time
ParkingLot regularLot = new ParkingLot(new HourlyFeeStrategy(50.0));
ParkingLot eventLot   = new ParkingLot(new FlatRateFeeStrategy(200.0));
```

### 🏢 Real World Usage

- **Java standard library** — `Collections.sort(list, comparator)`. The `Comparator` is a Strategy. You swap in a different comparator to change sort order without touching the sort algorithm.
- **Payment gateways** (Razorpay, PhonePe) — `PaymentStrategy` with `UpiPayment`, `CardPayment`, `WalletPayment` implementations. Checkout calls `strategy.pay(amount)` — doesn't care which payment rail runs.
- **Swiggy / Zomato delivery routing** — `RoutingStrategy` with `ShortestDistance`, `FastestDelivery`, `CostOptimised`. The order assignment engine plugs in different strategies based on time of day.
- **Log4j / SLF4J** — `Appender` implementations are Strategies. `FileAppender`, `ConsoleAppender`, `CloudWatchAppender` all implement the same interface. The logger calls `appender.append(event)` without knowing which one.

### 🧩 LLD Problems That Use Strategy Pattern

- **Parking Lot** — Strategy handles fee calculation. `HourlyFeeStrategy` for regular parking, `FlatRateFeeStrategy` for event days, `FreeStrategy` for the first 15 minutes. Parking lot calls `feeStrategy.calculate()` — doesn't know which runs.
- **BookMyShow / Movie Ticket Booking** — Strategy for pricing tiers. `PeakHourPricing` adds surcharge on weekend evenings, `StudentPricing` applies discount, `RegularPricing` is default. Seat booking calls `pricingStrategy.calculate(seat, time)`.
- **Rate Limiter (LLD)** — Strategy IS the entire core of this problem. `TokenBucketStrategy` allows burst traffic, `SlidingWindowStrategy` gives smooth rate enforcement, `FixedWindowStrategy` is simpler but has edge-case spikes. The limiter holds a strategy and calls `strategy.isAllowed(clientId)`.
- **Elevator System** — Strategy for floor scheduling algorithm. `FCFSStrategy` (first come first served), `SCANStrategy` (sweep up then down — the classic elevator algorithm), `OptimisedStrategy` (nearest cabin first). Controller calls `scheduler.nextFloor(pendingRequests)`.
- **Splitwise** — Strategy for expense splitting. `EqualSplitStrategy`, `ExactSplitStrategy`, `PercentageSplitStrategy` — each implements `split(amount, participants)` differently. Adding a new split type = one new class, nothing else changes.
- **LRU Cache** — Strategy for eviction policy. `LRUEvictionStrategy` (evict least recently used), `LFUEvictionStrategy` (evict least frequently used), `FIFOEvictionStrategy`. The cache calls `evictionStrategy.evict(cacheMap)` when it's full.
- **Cab Booking / Ride Sharing** — Strategy for surge pricing. `NoSurgeStrategy`, `TimeBasedSurgeStrategy` (peak hours), `DemandBasedSurgeStrategy` (dynamic 1.0x–3.0x). The fare calculator calls `surgeStrategy.multiplier(time, demand)`.
- **Stock Exchange / Order Book** — Strategy for order matching algorithm. `PriceTimePriorityStrategy` (standard exchange matching), `ProRataStrategy` (proportional fill at same price). Matching engine calls `matcher.match(buyOrder, orderBook)`.
- **Logger System** — Strategy for log formatting. `JsonFormatterStrategy`, `PlainTextFormatterStrategy`, `XMLFormatterStrategy`. The logger calls `formatter.format(logEvent)` before passing to the handler.
- **Library Management** — Strategy for fine calculation. `DailyFineStrategy` (fixed per day), `TieredFineStrategy` (increases after 7 days overdue), `WaiverStrategy` (for first-time offenders). Called as `fineStrategy.calculate(daysOverdue)`.
- **Meeting Room Reservation** — Strategy for room assignment. `NearestAvailableStrategy` (closest to the requester's floor), `LeastWastedCapacityStrategy` (smallest room that fits), `PriorityStrategy` (executives get best rooms). Called as `assignmentStrategy.assign(request, availableRooms)`.
- **Vending Machine** — Strategy for payment processing. `CashPaymentStrategy`, `CardPaymentStrategy`, `UpiPaymentStrategy`. The vending machine calls `paymentStrategy.processPayment(amount)` — same interface regardless of method.

### 🧭 When to Use vs When NOT to Use

| Use Strategy when | Do NOT use when |
|---|---|
| An operation has multiple valid algorithms | There's only one algorithm and it won't change |
| The algorithm needs to vary at runtime | The "variation" is just one boolean flag |
| Adding a new algorithm should not change existing code | Algorithms share significant state — a single class is cleaner |
| You want to test algorithms independently | The variation is trivial (just a parameter difference) |

**The common mistake:** Using Strategy when a simple parameter would do. If the only difference between two "strategies" is a multiplier value, pass the multiplier — don't create two classes. Strategy earns its complexity when algorithms have genuinely different logic.

---

## 🔬 Interview Q&As (Both Patterns)

### Q: "What is the Factory pattern and when would you use it?"
> Factory centralises object creation when the type depends on a runtime condition. Instead of `if-else` scattered across callers, one factory method owns the decision. Use it when you have multiple concrete types under a common interface and adding a new type should require zero changes in callers.

### Q: "What's the difference between Factory Method and Abstract Factory?"
> Factory Method creates one type of object — one factory, one product family. Abstract Factory creates families of related objects — one factory for `WindowsButton + WindowsCheckbox`, another for `MacButton + MacCheckbox`. Abstract Factory is for when you need consistency across multiple related types simultaneously. For most LLD interview problems, Factory Method is sufficient.

### Q: "What is the Strategy pattern and how is it different from just using if-else?"
> Strategy makes each algorithm a first-class object implementing a shared interface. The caller holds a reference to the interface and calls it — it never sees which algorithm is running. With if-else, adding a new algorithm means editing existing code, which risks breaking existing behaviour. With Strategy, you add a new class — existing code is untouched. Open for extension, closed for modification.

### Q: "How would you combine Factory and Strategy in a parking lot?"
> Factory creates the right `ParkingSpot` type based on vehicle size — the caller never calls `new CompactSpot()` directly. Strategy handles fee calculation — `HourlyFeeStrategy` for regular parking, `FlatRateFeeStrategy` for event parking, `FreeStrategy` for the first 15 minutes. The parking lot is injected with both: a factory to build spots, a strategy to calculate fees. Adding a new spot type or fee algorithm touches one class each — nothing else.

---

## 🧾 TL;DR — One Interviewer-Ready Line

> *"I'm using Factory to centralise spot creation — when I add a new spot type, I update one class. I'm using Strategy for fee calculation so the algorithm is swappable at runtime without touching the parking lot itself."*
