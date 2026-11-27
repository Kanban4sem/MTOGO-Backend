package mtogo.feature.order.presentation;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;
import io.restassured.config.JsonConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.path.json.config.JsonPathConfig.NumberReturnType;
import io.restassured.specification.RequestSpecification;
import mtogo.feature.order.OrderTestData;
import mtogo.feature.order.domain.OrderStatus;
import mtogo.security.JwtService;
import mtogo.testsupport.AuthenticatedEndpointTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@AuthenticatedEndpointTest
class DeliveryBonusControllerTest extends OrderTestData
{
    private static final String PATH = "/v1/delivery-agent/bonuses";

    @LocalServerPort
    int port;
    @Autowired
    JwtService tokens;

    @TestConfiguration
    static class FixedClock
    {
        @Bean
        @Primary
        Clock fixedClock()
        {
            return Clock.fixed(Instant.parse("2026-11-30T23:30:00Z"), ZoneOffset.UTC);
        }
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvFileSource(resources = "/testcases/order/delivery-bonus-controller/get-access.csv", numLinesToSkip = 1)
    void getAccess(String role, int expected, String reason)
    {
        RequestSpecification requester = switch (role)
        {
            case "ANONYMOUS" -> request();
            case "RESTAURANT" -> asUser(restaurantUser("owner@example.test", restaurant("Bistro", true)));
            default -> asUser(user("requester@example.test", role));
        };

        var response = requester.get(PATH).then().statusCode(expected);
        if (expected >= 400)
        {
            response.contentType("application/problem+json").body("status", is(expected));
        }
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvFileSource(resources = "/testcases/order/delivery-bonus-controller/get-month.csv", numLinesToSkip = 1)
    void getMonth(String month, int expected, String reason)
    {
        var response = asUser(deliveryAgent("agent")).queryParam("month", month).get(PATH).then().statusCode(expected);
        if (expected == 200)
        {
            response.body("month", is(month)).body("currency", is("DKK")).body("deliveredOrders", is(0))
                    .body("bonusAmount", is(new BigDecimal("0.00")));
        } else
        {
            response.contentType("application/problem+json").body("status", is(expected));
        }
    }

    @ParameterizedTest(name = "[{index}] {5}")
    @CsvFileSource(resources = "/testcases/order/delivery-bonus-controller/calculate-bonus.csv", numLinesToSkip = 1, nullValues = "NULL")
    void calculateBonus(String total, String commission, String deliveredAt, Integer deliveryRating, String expected,
            String reason)
    {
        long agentId = deliveryAgent("agent");
        delivered(restaurant("Bistro", true), customer(), agentId, deliveredAt, total, commission, deliveryRating);

        asUser(agentId).queryParam("month", deliveredAt.substring(0, 7)).get(PATH).then().statusCode(200)
                .body("deliveredOrders", is(1)).body("bonusAmount", is(new BigDecimal(expected)));
    }

    @Test
    void getUsesCopenhagenMonthsAndOnlyTheAgentsDeliveries()
    {
        long restaurantId = restaurant("Bistro", true);
        long customerId = customer();
        long agentId = deliveryAgent("agent");
        long otherAgentId = deliveryAgent("other");
        delivered(restaurantId, customerId, agentId, "2026-10-31T22:59:59.999999Z", "125.00", "6.00", null);
        delivered(restaurantId, customerId, agentId, "2026-10-31T23:00:00Z", "125.00", "6.00", null);
        delivered(restaurantId, customerId, agentId, "2026-11-30T22:59:59.999999Z", "125.00", "0.03", 6);
        delivered(restaurantId, customerId, agentId, "2026-11-30T23:00:00Z", "250.00", "15.00", 6);
        delivered(restaurantId, customerId, otherAgentId, "2026-11-10T12:00:00Z", "250.00", "15.00", 6);
        delivered(restaurantId, customerId, null, "2026-11-10T12:00:00Z", "250.00", "15.00", 6);
        Instant pickedUpAt = Instant.parse("2026-11-10T12:00:00Z");
        long openOrderId = orderAt(restaurantId, customerId, "125.00",
                new EnumMap<>(Map.of(OrderStatus.PICKED_UP, pickedUpAt)));
        setOrder(openOrderId, "PICKED_UP", agentId);

        asUser(agentId).queryParam("month", "2026-11").get(PATH).then().statusCode(200).body("month", is("2026-11"))
                .body("deliveredOrders", is(2)).body("bonusAmount", is(new BigDecimal("1.00")));
        asUser(agentId).get(PATH).then().statusCode(200).body("month", is("2026-12")).body("deliveredOrders", is(1))
                .body("bonusAmount", is(new BigDecimal("2.05")));
    }

    @Test
    void newlySubmittedFeedbackUpdatesTheDeliveryMonth()
    {
        long agentId = deliveryAgent("agent");
        long customerId = customer();
        long orderId = delivered(restaurant("Bistro", true), customerId, agentId, "2026-11-10T12:00:00Z", "125.00",
                "6.00", null);

        asUser(agentId).queryParam("month", "2026-11").get(PATH).then().statusCode(200).body("bonusAmount",
                is(new BigDecimal("0.75")));

        asUser(customerId).contentType("application/json")
                .body(Map.of("foodRating", 1, "overallRating", 1, "deliveryRating", 6))
                .post("/v1/customer/orders/{id}/feedback", orderId).then().statusCode(201);

        asUser(agentId).queryParam("month", "2026-11").get(PATH).then().statusCode(200).body("bonusAmount",
                is(new BigDecimal("1.05")));
    }

    private long delivered(long restaurantId, long customerId, Long agentId, String deliveredAt, String total,
            String commission, Integer deliveryRating)
    {
        long orderId = orderAt(restaurantId, customerId, total,
                new EnumMap<>(Map.of(OrderStatus.DELIVERED, Instant.parse(deliveredAt))));
        jdbc.update("UPDATE orders SET delivery_agent_id = ?, commission = ?, service_fee = 5.00 WHERE id = ?", agentId,
                new BigDecimal(commission), orderId);
        if (deliveryRating != null)
        {
            addFeedback(orderId, deliveryRating);
        }
        return orderId;
    }

    private void addFeedback(long orderId, int deliveryRating)
    {
        jdbc.update(
                "INSERT INTO order_feedback (order_id, food_rating, overall_rating, delivery_rating) VALUES (?, 6, 6, ?)",
                orderId, deliveryRating);
    }

    private RequestSpecification request()
    {
        return given().port(port).config(RestAssuredConfig.config()
                .jsonConfig(JsonConfig.jsonConfig().numberReturnType(NumberReturnType.BIG_DECIMAL)));
    }

    private RequestSpecification asUser(long userId)
    {
        return request().auth().oauth2(tokens.issue(userId, 0).value());
    }
}
