package mtogo.feature.order.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import mtogo.exceptions.ApiException;
import mtogo.feature.order.OrderTestData;
import mtogo.feature.order.presentation.dto.request.CreateOrderFeedbackDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class OrderFeedbackServiceTest extends OrderTestData
{
    private static final CreateOrderFeedbackDTO FEEDBACK = new CreateOrderFeedbackDTO("Good meal", "Arrived hot",
            BigDecimal.valueOf(6), BigDecimal.valueOf(5), BigDecimal.valueOf(4));
    private static final Map<String, Object> STORED_FEEDBACK = Map.of("title", "Good meal", "description",
            "Arrived hot", "food_rating", 6, "overall_rating", 5, "delivery_rating", 4);
    private static final Instant ORDER_TIME = Instant.parse("2026-11-25T12:00:00Z");

    @Autowired
    OrderFeedbackService feedback;

    @ParameterizedTest(name = "[{index}] {4}")
    @CsvFileSource(resources = "/testcases/order/order-feedback-service/create-eligibility.csv", numLinesToSkip = 1)
    @DisplayName("Create feedback - saves all fields once and only for a customer's own delivered order")
    void createEligibility(String order, OrderStatus status, boolean hasFeedback, String expected, String reason)
    {
        long customerId = customer();
        long ownerId = order.equals("OTHER") ? user("other@example.test", "CUSTOMER") : customerId;
        EnumMap<OrderStatus, Instant> statusTimes = new EnumMap<>(OrderStatus.class);
        statusTimes.put(status, ORDER_TIME);
        long orderId = orderAt(restaurant("Lunch restaurant", true), ownerId, "90.00", statusTimes);
        long requestedId = order.equals("UNKNOWN") ? orderId + 1 : orderId;
        if (hasFeedback)
        {
            feedback.create(customerId, orderId, FEEDBACK);
        }

        if (expected.equals("ACCEPT"))
        {
            feedback.create(customerId, requestedId, FEEDBACK);
        } else
        {
            CreateOrderFeedbackDTO replacement = new CreateOrderFeedbackDTO("Replacement", "Different comment",
                    BigDecimal.ONE, BigDecimal.TWO, BigDecimal.valueOf(3));
            ApiException exception = assertThrows(ApiException.class,
                    () -> feedback.create(customerId, requestedId, replacement));
            assertEquals(expected, exception.getClass().getSimpleName());
        }

        var rows = jdbc.queryForList("""
                SELECT order_id, title, description, food_rating, overall_rating, delivery_rating
                FROM order_feedback
                """);
        if (hasFeedback || expected.equals("ACCEPT"))
        {
            assertEquals(1, rows.size());
            var feedbackRow = rows.getFirst();
            assertEquals(orderId, feedbackRow.remove("order_id"));
            assertEquals(STORED_FEEDBACK, feedbackRow);
        } else
        {
            assertEquals(List.of(), rows);
        }
    }
}
