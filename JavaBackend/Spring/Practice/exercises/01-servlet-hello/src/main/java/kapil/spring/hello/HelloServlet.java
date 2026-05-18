package kapil.spring.hello;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicInteger;

// jakarta.servlet.annotation.WebServlet — Servlet 3.0+ annotation registration
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Day 2 — hello-world servlet.
 *
 * <p>Demonstrates the full lifecycle:
 * <ul>
 *   <li>{@code init()} fires ONCE before any request — logs and reads an init param</li>
 *   <li>{@code doGet()} fires per-request, concurrently — logs the thread name</li>
 *   <li>{@code destroy()} fires ONCE at shutdown</li>
 * </ul>
 *
 * <p>The {@link AtomicInteger} request counter shows the correct pattern for
 * shared mutable state — a plain {@code int} would race under load.
 */
@WebServlet(
    name = "helloServlet",
    urlPatterns = "/hello",
    loadOnStartup = 1,
    initParams = {
        @jakarta.servlet.annotation.WebInitParam(name = "greeting", value = "Hello")
    }
)
public class HelloServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(HelloServlet.class);

    // Configuration set ONCE in init(); never mutated after that.
    // Safe because all subsequent reads are pure reads of a final-ish field
    // (publication is guaranteed by the happens-before edge between init()
    // and the first service() call).
    private String greeting;

    // Per-request counter — uses Atomic* because service() is concurrent.
    private final AtomicInteger requestCount = new AtomicInteger(0);

    @Override
    public void init() throws ServletException {
        ServletConfig config = getServletConfig();
        this.greeting = config.getInitParameter("greeting");
        if (this.greeting == null) {
            this.greeting = "Hello";
        }
        log.info("HelloServlet.init() — greeting='{}', loadOnStartup=1 → init fires at boot",
                 this.greeting);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {

        int n = requestCount.incrementAndGet();
        String thread = Thread.currentThread().getName();

        log.info("doGet — request #{} on thread '{}' — URI={}, query={}",
                 n, thread, req.getRequestURI(), req.getQueryString());

        // Read an optional ?name=... query parameter (raw API; no @RequestParam here).
        String name = req.getParameter("name");
        if (name == null || name.isBlank()) {
            name = "world";
        }

        // CRITICAL: status + headers BEFORE writing the body.
        res.setStatus(HttpServletResponse.SC_OK);
        res.setContentType("text/plain;charset=UTF-8");
        res.setHeader("X-Request-Count", String.valueOf(n));

        // try-with-resources flushes + closes the writer on exit.
        try (PrintWriter out = res.getWriter()) {
            out.println(this.greeting + ", " + name + "!");
            out.println("(handled by " + thread + ", request #" + n + ")");
        }
    }

    @Override
    public void destroy() {
        log.info("HelloServlet.destroy() — total requests handled: {}", requestCount.get());
        super.destroy();
    }
}
