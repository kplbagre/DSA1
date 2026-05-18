# Spring Practice — Setup & How-To-Run

> Companion to `../spring-10-hour-plan.md` and `../spring-prep-log.md`. This folder holds the **runnable Java code** for the Spring foundation track.

---

## 🎯 What's in here

Two kinds of code, kept separate on purpose:

```
Practice/
├── README.md                    ← THIS FILE (setup + run instructions)
│
├── exercises/                   ← Per-topic drills (small, focused, ~5-15 min each)
│   ├── 01-servlet-hello/        ← Day 2 — first servlet, embedded Jetty
│   ├── 02-bean-discovery/       ← Day 3 — ApplicationContext + @Component scan
│   ├── 03-bean-lifecycle/       ← Day 4 — @PostConstruct, scopes
│   ├── 04-aop-self-call-trap/   ← Day 5 — THE killer @Transactional demo
│   ├── 05-dispatcher-trace/     ← Day 6 — log DispatcherServlet call stack
│   ├── 06-autoconfig-peek/      ← Day 7 — Boot --debug, see what's auto-wired
│   ├── 07-profiles-demo/        ← Day 8 — @Profile + properties precedence
│   └── 08-jpa-lazy-loading/     ← Day 9 — LazyInitializationException, fix 3 ways
│
└── growing-app/                 ← Same endpoint, three iterations
    ├── v1-servlet/              ← Day 2 — raw servlet + Jetty (~50 LoC + web.xml)
    ├── v2-spring-mvc/           ← Day 6 — Spring MVC + Java config (no Boot)
    └── v3-spring-boot/          ← Day 7 — Spring Boot (~10 LoC)
```

**Why two folders:**

- **`exercises/`** — small, focused drills. Run, see output, internalize one concept, move on. No long-running project here.
- **`growing-app/`** — the *same* `GET /orders/{id}` endpoint solved three different ways. Side-by-side comparison is the lesson.

> Each exercise/version is its own self-contained Maven project so you can run them independently without cross-talk.

---

## 🛠️ Prerequisites

### One-time setup

| Tool | Version | Why | How to check |
| --- | --- | --- | --- |
| JDK | **17+** | Spring 6 / Spring Boot 3 require 17 | `java -version` |
| Maven | 3.6+ | Build tool used by every exercise | `mvn -v` |
| IDE | IntelliJ IDEA (Community is fine) | Industry standard; best Spring support | — |
| Git | any recent | (already installed for kapil-kb repo) | `git --version` |

**Corporate-network notes (eg, in your app):**
- JDK should already be installed via your standard dev setup (`brew install --cask` may be blocked on corporate networks, but `sdkman` and an internal Maven mirror usually work)
- For Maven, use the corporate `settings.xml` if you have one — gives you access to your org's internal artifact repository (needed if any exercise pulls internal libs; the standard Spring deps are on Maven Central and don't need it)

### Quick environment check

Run this once from this `Practice/` folder before starting Day 2:

```bash
java -version    # must show 17 or higher
mvn -v           # must show 3.6 or higher
mvn -version | grep "Java version"  # confirms Maven uses your JDK 17
```

If any of these fail, fix before proceeding — debugging Spring issues on a broken JDK setup wastes hours.

---

## 🚀 How to run an exercise

Each exercise folder will have its own `pom.xml`. The pattern is consistent:

```bash
# From an exercise folder, e.g., exercises/01-servlet-hello/
cd exercises/01-servlet-hello

# Build (downloads deps first time — may take a minute)
mvn clean package

# Run — exact command varies by exercise type:
#  - Servlet (embedded Jetty):
mvn jetty:run
#  - Spring (java -jar):
java -jar target/*.jar
#  - Spring Boot:
mvn spring-boot:run
```

The exact run command is documented in each exercise's own `README.md` (created when that day's note is written).

**Default ports:** every exercise binds to `8080` unless otherwise noted. If a previous exercise didn't shut down cleanly, you'll get `Address already in use`. Fix:

```bash
lsof -ti :8080 | xargs kill -9   # nuke whatever's on 8080
```

---

## 🌱 The "growing app" — three versions of the same endpoint

The **same endpoint** — `GET /orders/{id}` → returns a hard-coded `Order` JSON — gets built three times. The pedagogy is in the comparison.

| Version | Built on | Why this version exists |
| --- | --- | --- |
| `v1-servlet/` | Plain `HttpServlet` + embedded Jetty | Shows what raw servlet code looks like. You write all the boilerplate: URL parsing, JSON serialization (manual), response writing. |
| `v2-spring-mvc/` | Spring MVC + Java `@Configuration` (no Boot) | Shows what DI + `DispatcherServlet` removed. You stop writing URL routing, stop writing JSON serialization. But you still configure `DispatcherServlet`, view resolver, message converter by hand. |
| `v3-spring-boot/` | Spring Boot | Shows what auto-config removed. You configure literally nothing — Boot wires it all from the classpath. |

**The reveal:** Open all three folders side-by-side. Count lines. v1 might be 100 lines including config. v3 is ~10 lines. The 90 lines you didn't have to write in v3 are what every annotation (`@RestController`, `@SpringBootApplication`) abstracted away. **That's the mental model.**

---

## 📁 Per-exercise structure (what to expect)

Every exercise folder will have:

```
exercises/0N-name/
├── README.md            ← What this exercise demonstrates, how to run, expected output
├── pom.xml              ← Maven project (each is self-contained)
└── src/
    ├── main/
    │   ├── java/        ← The actual demo code
    │   └── resources/   ← application.properties, web.xml, etc.
    └── test/
        └── java/        ← Sometimes a JUnit test that runs the assertion
```

**Read the per-exercise README first.** It will state:
- What concept the exercise illustrates (links back to the DeepDive note)
- Step-by-step run instructions
- Expected output / observable behavior
- The "now do this manually" follow-up (e.g., "switch from constructor injection to field injection; observe what breaks in the test")

---

## 🐞 Common setup issues

| Symptom | Likely cause | Fix |
| --- | --- | --- |
| `Unsupported class file major version 61` | Maven using older JDK than the code targets | Set `JAVA_HOME` to JDK 17. Verify `mvn -v` shows Java 17. |
| `Address already in use: bind` | Previous exercise still bound to 8080 | `lsof -ti :8080 \| xargs kill -9` |
| `Plugin org.springframework.boot:... not found` | Corporate Maven `settings.xml` blocking Central | Check `~/.m2/settings.xml` mirrors — temporarily comment out for personal exercises |
| `No qualifying bean of type ...` | `@ComponentScan` missing your package OR you forgot `@Service` on the impl | Verify the `@SpringBootApplication` (or `@ComponentScan`) class is in a package ABOVE the package containing your beans |
| Tests pass but actual run fails | Test profile differs from default profile | `mvn spring-boot:run -Dspring-boot.run.profiles=test` to mirror the test config |
| `LazyInitializationException` (Day 9) | This is **intentional** — Day 9's exercise demonstrates this. Read the exercise README for the three fixes. | (don't fix on first run — observe the failure first) |

---

## 🪜 Workspace conventions

- **Java version** in every `pom.xml`: 17
- **Maven coordinates** for exercise N: `kapil.spring.exercises:0N-name:1.0-SNAPSHOT`
- **Coding style:** matches `../../../AGENTS.md` rules (one statement per line, always-braced blocks, spaces around operators)
- **Logging:** SLF4J + Logback (Spring's default). No `System.out.println` in any committed code — use a logger.
- **Test framework:** JUnit 5. Skip writing tests for trivial demos — but Days 5, 9 demos benefit from a JUnit assertion to make the trap explicit.

---

## 🔁 What happens when

| Day | Practice deliverable |
| --- | --- |
| 1 | (none — reading only) |
| 2 | `exercises/01-servlet-hello/` + `growing-app/v1-servlet/` |
| 3 | `exercises/02-bean-discovery/` |
| 4 | `exercises/03-bean-lifecycle/` |
| 5 | `exercises/04-aop-self-call-trap/` ⭐ (the killer demo) |
| 6 | `exercises/05-dispatcher-trace/` + `growing-app/v2-spring-mvc/` |
| 7 | `exercises/06-autoconfig-peek/` + `growing-app/v3-spring-boot/` |
| 8 | `exercises/07-profiles-demo/` |
| 9 | `exercises/08-jpa-lazy-loading/` |
| 10 | (no new exercises — walk all three growing-app versions side-by-side, mock interview) |

---

## 🧾 TL;DR

- **exercises/** = small drills for each topic
- **growing-app/** = same endpoint built 3 ways, side-by-side comparison
- Run with `mvn jetty:run` / `mvn spring-boot:run` / `java -jar target/*.jar` depending on exercise type
- JDK 17, Maven 3.6+, IntelliJ — that's the whole toolchain
- Each exercise has its own README with concept-link, run steps, expected output

When in doubt about how to run something — read that exercise's own `README.md` first.
