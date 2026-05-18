package kapil.spring.orders;

import java.math.BigDecimal;

/**
 * Plain Java record representing an order.
 *
 * <p>v1 (this version) hand-rolls JSON serialization in {@link OrderServlet}.
 * v2 (Spring MVC) will let Jackson handle this automatically via {@code @ResponseBody}.
 * v3 (Spring Boot) will make Jackson registration disappear into auto-config.
 */
public record Order(String id, String customer, BigDecimal total, String status) {

    /**
     * Hand-rolled JSON serialization — the v1 lesson.
     *
     * <p>Note all the things we have to do manually here:
     * <ul>
     *   <li>quote field names and string values</li>
     *   <li>escape any embedded quotes (omitted here — we trust the inputs)</li>
     *   <li>format the BigDecimal without surrounding quotes (it's a number, not a string)</li>
     *   <li>concatenate fields in the right order with commas</li>
     * </ul>
     *
     * <p>One Jackson dependency + one annotation makes all of this go away. That's
     * what v2 demonstrates.
     */
    public String toJson() {
        return "{"
            + "\"id\":\"" + id + "\","
            + "\"customer\":\"" + customer + "\","
            + "\"total\":" + total.toPlainString() + ","
            + "\"status\":\"" + status + "\""
            + "}";
    }
}
