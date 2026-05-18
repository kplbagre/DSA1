package kapil.spring.orders;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Growing app — version 1: raw servlet.
 *
 * <p>Endpoint contract: {@code GET /orders/{id}} → returns a JSON {@link Order}.
 *
 * <p>This version writes all the boilerplate by hand:
 * <ul>
 *   <li>URL parsing — extracting {@code {id}} from the path manually</li>
 *   <li>Validation — checking the path shape ourselves</li>
 *   <li>JSON serialization — hand-rolled in {@link Order#toJson()}</li>
 *   <li>Status codes and Content-Type — set explicitly on the response</li>
 * </ul>
 *
 * <p>Compare:
 * <ul>
 *   <li>v2 (Spring MVC) replaces all of this with {@code @GetMapping("/orders/{id}")}
 *       + {@code @PathVariable} + {@code @ResponseBody}. The class shrinks from this size
 *       to ~5 lines of actual logic.</li>
 *   <li>v3 (Spring Boot) removes the configuration on top of v2 — same controller,
 *       no Java config, no DispatcherServlet wiring.</li>
 * </ul>
 *
 * <p>The URL pattern {@code /orders/*} catches any path under {@code /orders/}.
 * The container then exposes the path-after-mapping as {@code req.getPathInfo()},
 * which we parse manually to extract the {@code {id}} segment.
 */
@WebServlet(name = "orderServlet", urlPatterns = "/orders/*", loadOnStartup = 1)
public class OrderServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(OrderServlet.class);

    /**
     * Hard-coded "database." In a real app this would be a service / repository
     * injected via DI — but we haven't met DI yet (Day 3). Here we just have a map.
     *
     * <p>Marked {@code final} so the reference doesn't change; the {@code Map.of}
     * factory returns an immutable map, so we don't need a {@link java.util.concurrent.ConcurrentHashMap}.
     * Safe to share across all request threads.
     */
    private final Map<String, Order> orders = Map.of(
        "1", new Order("1", "Alice",   new BigDecimal("129.99"), "SHIPPED"),
        "2", new Order("2", "Bob",     new BigDecimal("42.50"),  "PENDING"),
        "3", new Order("3", "Charlie", new BigDecimal("999.00"), "DELIVERED")
    );

    @Override
    public void init() throws ServletException {
        log.info("OrderServlet.init() — registered at /orders/*, {} orders preloaded",
                 orders.size());
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {

        // STEP 1 — parse the {id} out of the URL by hand.
        //
        // For req.getRequestURI() = "/orders/123":
        //   req.getServletPath() = "/orders"
        //   req.getPathInfo()    = "/123"
        //
        // pathInfo is null if the request was exactly "/orders" with no trailing /.
        String pathInfo = req.getPathInfo();
        log.info("doGet — URI={}, pathInfo={}, thread={}",
                 req.getRequestURI(), pathInfo, Thread.currentThread().getName());

        if (pathInfo == null || pathInfo.equals("/")) {
            writeError(res, HttpServletResponse.SC_BAD_REQUEST,
                       "missing order id — usage: GET /orders/{id}");
            return;
        }

        // Strip the leading "/" and split. We're paranoid here — the v1 lesson is
        // that the raw API gives you NOTHING for free.
        String[] segments = pathInfo.substring(1).split("/");
        if (segments.length != 1 || segments[0].isBlank()) {
            writeError(res, HttpServletResponse.SC_BAD_REQUEST,
                       "expected exactly one path segment after /orders/");
            return;
        }
        String id = segments[0];

        // STEP 2 — look up the order.
        Order order = orders.get(id);
        if (order == null) {
            writeError(res, HttpServletResponse.SC_NOT_FOUND,
                       "no order with id=" + id);
            return;
        }

        // STEP 3 — set status, content type, and headers BEFORE writing the body.
        res.setStatus(HttpServletResponse.SC_OK);
        res.setContentType("application/json;charset=UTF-8");
        res.setHeader("X-Served-By", "v1-raw-servlet");

        // STEP 4 — write the hand-rolled JSON, flushed via try-with-resources.
        try (PrintWriter out = res.getWriter()) {
            out.write(order.toJson());
        }
    }

    /**
     * Hand-rolled error response. v2 / v3 will replace this with
     * {@code @ExceptionHandler} or {@code ResponseEntity.status(404).body(...)}.
     */
    private void writeError(HttpServletResponse res, int status, String message)
            throws IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        try (PrintWriter out = res.getWriter()) {
            out.write("{\"error\":\"" + message + "\"}");
        }
    }

    @Override
    public void destroy() {
        log.info("OrderServlet.destroy()");
        super.destroy();
    }
}
