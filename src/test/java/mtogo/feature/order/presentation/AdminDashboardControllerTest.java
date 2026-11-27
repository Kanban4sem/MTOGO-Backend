package mtogo.feature.order.presentation;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import mtogo.feature.order.OrderTestData;
import mtogo.feature.order.domain.OrderStatus;
import mtogo.security.JwtService;
import mtogo.testsupport.AuthenticatedEndpointTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@AuthenticatedEndpointTest
class AdminDashboardControllerTest extends OrderTestData
{
    private static final String PATH = "/v1/admin/dashboard";
    private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");

    @LocalServerPort
    int port;
    @Autowired
    JwtService tokens;

    enum Requester
    {
        ANONYMOUS, CUSTOMER, RESTAURANT, DELIVERY_AGENT, ADMIN
    }

    @TestConfiguration
    static class FixedClock
    {
        @Bean
        @Primary
        Clock fixedClock()
        {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvFileSource(resources = "/testcases/order/admin-dashboard-controller/get-access.csv", numLinesToSkip = 1)
    @DisplayName("Get - only admins open the dashboard")
    void getAccess(Requester role, int expected, String reason)
    {
        RequestSpecification requester = given().port(port);
        Long userId = switch (role)
        {
            case ANONYMOUS -> null;
            case RESTAURANT -> restaurantManager("requester@example.test", restaurant("Bakery", true));
            default -> user("requester@example.test", role.name());
        };
        if (userId != null)
        {
            requester = requester.auth().oauth2(tokens.issue(userId, 0).value());
        }

        Response result = requester.get(PATH);

        result.then().statusCode(expected);
        if (expected >= 400)
        {
            result.then().contentType("application/problem+json").body("status", is(expected));
        }
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvFileSource(resources = "/testcases/order/admin-dashboard-controller/get-days.csv", numLinesToSkip = 1, nullValues = "NULL")
    @DisplayName("Get - takes a period of 1 to 365 days, 7 by default")
    void getDays(String days, int expected, String reason)
    {
        RequestSpecification requester = asAdmin();
        if (days != null)
        {
            requester = requester.queryParam("days", days);
        }

        Response result = requester.get(PATH);

        result.then().statusCode(expected);
        if (expected == 200)
        {
            result.then().body("days", is(days == null ? 7 : Integer.parseInt(days)));
        } else
        {
            result.then().contentType("application/problem+json").body("status", is(400));
        }
    }

    @Test
    @DisplayName("Get - returns the figures of all of MTOGO and of every restaurant, with null minutes and statuses at 0")
    void getReturnsTheFigures()
    {
        long alphaId = restaurant("Alpha Sushi", true);
        long betaId = restaurant("Beta Burger", false);
        long customerId = customer();
        Instant placedAt = NOW.minus(Duration.ofHours(2));
        orderAt(alphaId, customerId, "125.50",
                new EnumMap<>(Map.of(OrderStatus.PLACED, placedAt, OrderStatus.ACCEPTED, minutesAfter(placedAt, 4),
                        OrderStatus.READY, minutesAfter(placedAt, 22), OrderStatus.PICKED_UP,
                        minutesAfter(placedAt, 28), OrderStatus.DELIVERED, minutesAfter(placedAt, 45))));
        orderAt(alphaId, customerId, "60.00", new EnumMap<>(Map.of(OrderStatus.PLACED, placedAt)));

        Response result = asAdmin().queryParam("days", 7).get(PATH);

        result.then().statusCode(200).body("days", is(7)).body("total.openOrders", is(1))
                .body("total.openOrdersByStatus.PLACED", is(1)).body("total.openOrdersByStatus.ACCEPTED", is(0))
                .body("total.openOrdersByStatus.READY", is(0)).body("total.openOrdersByStatus.PICKED_UP", is(0))
                .body("total.openOrdersByStatus.size()", is(4)).body("total.ordersPer24Hours", is(0.3f))
                .body("total.rejectedOrders", is(0)).body("total.sales", is(125.50f))
                .body("total.averageDeliveryTimeMinutes", is(45)).body("total.averageStepMinutes.acceptance", is(4))
                .body("total.averageStepMinutes.preparation", is(18)).body("total.averageStepMinutes.pickup", is(6))
                .body("total.averageStepMinutes.transit", is(17))
                .body("restaurants.id", contains((int) alphaId, (int) betaId))
                .body("restaurants.name", contains("Alpha Sushi", "Beta Burger"))
                .body("restaurants.acceptsOrders", contains(true, false)).body("restaurants[0]", not(hasKey("figures")))
                .body("restaurants[0]", not(hasKey("isAcceptingOrders"))).body("restaurants[0].openOrders", is(1))
                .body("restaurants[0].averageDeliveryTimeMinutes", is(45)).body("restaurants[1].openOrders", is(0))
                .body("restaurants[1].openOrdersByStatus.PLACED", is(0))
                .body("restaurants[1].openOrdersByStatus.ACCEPTED", is(0))
                .body("restaurants[1].openOrdersByStatus.READY", is(0))
                .body("restaurants[1].openOrdersByStatus.PICKED_UP", is(0))
                .body("restaurants[1]", hasKey("averageDeliveryTimeMinutes"))
                .body("restaurants[1].averageDeliveryTimeMinutes", is(nullValue()))
                .body("restaurants[1].averageStepMinutes",
                        allOf(hasKey("acceptance"), hasKey("preparation"), hasKey("pickup"), hasKey("transit")))
                .body("restaurants[1].averageStepMinutes.acceptance", is(nullValue()))
                .body("restaurants[1].averageStepMinutes.transit", is(nullValue()));
        // The parsed JSON loses trailing zeros, so the decimal places are checked in
        // the raw body.
        String body = result.asString();
        assertThat(body, allOf(containsString("\"sales\":125.50"), containsString("\"sales\":0.00"),
                containsString("\"ordersPer24Hours\":0.0")));
    }

    private RequestSpecification asAdmin()
    {
        return given().port(port).auth().oauth2(tokens.issue(user("admin@example.test", "ADMIN"), 0).value());
    }

    private static Instant minutesAfter(Instant start, long minutes)
    {
        return start.plus(Duration.ofMinutes(minutes));
    }
}
