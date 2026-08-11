# JPMorgan — Code Review Round Guide

> **Confirmed:** Round C at JPMC almost always has a code review task.
> Format: a "working" Java production snippet is shared on HackerRank/screen share.
> You must identify problems and suggest improvements — aloud, as you would in a real PR review.
> Time: ~20 minutes of the round.

---

## 🎯 What They Are Looking For

JPMC code review is NOT about finding compile errors. The code runs fine. They want you to catch:

| Category | What to look for |
|---|---|
| **Thread safety** | Shared mutable state accessed without synchronization |
| **Null safety** | Unguarded `.get()` on Optional, unchecked return values |
| **Exception handling** | Swallowed exceptions, catching `Exception` broadly |
| **Resource leaks** | Streams, connections, files not closed (`try-with-resources`) |
| **Security** | SQL injection, hardcoded credentials, sensitive data in logs |
| **Performance** | N+1 queries, unnecessary object creation in loops, String concat in loop |
| **Code quality** | Magic numbers, misleading variable names, dead code |
| **Concurrency** | Race conditions, deadlock risk, `HashMap` in multi-threaded context |

---

## 🔬 Common Snippet Types + What to Say

### Snippet 1 — Thread-safety issue with HashMap

```java
// Given code (works in prod, single-threaded):
public class SessionStore {
    private Map<String, Session> sessions = new HashMap<>();

    public void put(String id, Session session) {
        sessions.put(id, session);
    }

    public Session get(String id) {
        return sessions.get(id);
    }
}
```

**What you should say:**
> "This works fine in a single-threaded environment, but if `SessionStore` is a singleton Spring bean (which it will be by default), multiple request threads will hit `put()` and `get()` concurrently. `HashMap` is not thread-safe — concurrent `put()` calls during resize can cause infinite loops in Java 7, and even in Java 8+ you can get data corruption or lost writes.
>
> Fix: replace `HashMap` with `ConcurrentHashMap`. If the full operation needs to be atomic (like check-then-act), use `computeIfAbsent()` or a `ReentrantLock` around the compound operation."

---

### Snippet 2 — Exception swallowing

```java
// Given code:
public String fetchConfig(String key) {
    try {
        return configService.get(key);
    } catch (Exception e) {
        e.printStackTrace();
        return null;
    }
}
```

**What you should say:**
> "Three issues here. First, catching the broad `Exception` type hides bugs — a `NullPointerException` or an `IllegalArgumentException` are programming errors that should propagate, not be silently swallowed. Second, `e.printStackTrace()` writes to stderr, bypassing the logging framework — log levels, log aggregation (Splunk/ELK), and correlation IDs are all lost. Third, returning `null` forces every caller to null-check the result, which is error-prone.
>
> Better: catch the specific exception (`ConfigNotFoundException`), log via `log.error(\"Config fetch failed for key {}\", key, e)`, and either rethrow as a domain exception or return `Optional<String>` to make the null case explicit."

---

### Snippet 3 — SQL injection

```java
// Given code:
public User findUser(String username) {
    String sql = "SELECT * FROM users WHERE username = '" + username + "'";
    return jdbcTemplate.queryForObject(sql, User.class);
}
```

**What you should say:**
> "This is a SQL injection vulnerability — a critical security issue. If `username` is `' OR '1'='1`, the query becomes `WHERE username = '' OR '1'='1'`, returning all users. An attacker can drop tables, extract data, or bypass authentication.
>
> Fix: always use parameterized queries with `?` placeholders:
```java
String sql = "SELECT * FROM users WHERE username = ?";
return jdbcTemplate.queryForObject(sql, new Object[]{username}, User.class);
```
> The JDBC driver sends the query and parameters separately — the value is never interpreted as SQL."

---

### Snippet 4 — String concatenation in a loop (performance)

```java
// Given code:
public String buildReport(List<String> lines) {
    String report = "";
    for (String line : lines) {
        report += line + "\n";  // creates new String object each iteration
    }
    return report;
}
```

**What you should say:**
> "String is immutable in Java — `report += line` creates a new String object in each iteration, copying all previous content. For N lines this is O(N²) time and O(N²) allocations — for a 10K-line report that's 10K temporary String objects and ~50MB of unnecessary GC pressure.
>
> Fix: use `StringBuilder` which is mutable and pre-allocates capacity:
```java
StringBuilder sb = new StringBuilder(lines.size() * 80); // estimated capacity
for (String line : lines) {
    sb.append(line).append('\n');  // char literal, not string
}
return sb.toString();
```
> Or the one-liner with streams: `String.join(\"\n\", lines)`."

---

### Snippet 5 — Resource leak

```java
// Given code:
public void processFile(String path) throws IOException {
    BufferedReader reader = new BufferedReader(new FileReader(path));
    String line;
    while ((line = reader.readLine()) != null) {
        process(line);
    }
    reader.close(); // only closes if no exception is thrown
}
```

**What you should say:**
> "If `process(line)` throws an exception, the `reader.close()` call is never reached — the file descriptor leaks. Under load (many files processed), this can exhaust OS file descriptors and cause `Too many open files` errors.
>
> Fix: use try-with-resources — the compiler inserts the `close()` in a finally block automatically, even if an exception is thrown:
```java
try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
    String line;
    while ((line = reader.readLine()) != null) {
        process(line);
    }
}
```

---

### Snippet 6 — Sensitive data in logs

```java
// Given code:
public void authenticate(String username, String password) {
    log.info("Authenticating user: {} with password: {}", username, password);
    // ...
}
```

**What you should say:**
> "Logging credentials in plaintext is a critical security violation — at JPMC this would be a compliance/PCI-DSS finding. Logs are aggregated to Splunk/ELK, accessible by ops teams, and often shipped to third-party services. An attacker with log access gains credentials.
>
> Never log passwords, API keys, SSNs, card numbers, or tokens. Log only the username and the authentication outcome. If you need to debug auth issues, log a correlation ID and look up the attempt in the audit table."

---

## 📋 How to Structure Your Review (aloud, in the interview)

```
Step 1: Read the code top-to-bottom (15-20 seconds, silent)
Step 2: "The code does X — let me walk you through what I see..."
Step 3: Start with the highest severity finding (security > correctness > performance > style)
Step 4: For each finding:
         (a) Name the problem
         (b) Explain why it matters (production impact)
         (c) Show the fix
Step 5: End with: "Are there any specific concerns you'd like me to dig deeper on?"
```

**Don't say:** "This could be improved."
**Do say:** "This is a thread-safety issue. In production with concurrent requests, `HashMap.put()` during a resize can cause data corruption — the fix is `ConcurrentHashMap`."

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Aug 10, 2026 | File created from 2024–2026 JPMC interview reports. Code review round confirmed in 4+ reports. |
