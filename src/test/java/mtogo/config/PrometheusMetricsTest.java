package mtogo.config;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.util.List;
import mtogo.feature.order.OrderTestData;
import mtogo.feature.order.domain.DeliveryAction;
import mtogo.feature.order.domain.OrderService;
import mtogo.feature.order.presentation.dto.request.PlaceOrderDTO;
import mtogo.feature.order.presentation.dto.request.UpdateDeliveryAgentOrderDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMetrics
class PrometheusMetricsTest extends OrderTestData
{
    @LocalServerPort
    int port;
    @LocalManagementPort
    int managementPort;
    @Autowired
    OrderService orders;

    @Test
    @DisplayName("Prometheus - exports the order metrics and the API request metrics with their buckets, without actuator requests")
    void prometheusExportsOrderAndRequestMetrics()
    {
        given().port(port).get("/v1/restaurants").then().statusCode(200);
        deliverOneOrder();
        given().port(managementPort).get("/actuator/health").then().statusCode(200);

        String metrics = given().port(managementPort).get("/actuator/prometheus").then().statusCode(200).extract()
                .asString();

        assertThat(metrics,
                allOf(containsString("mtogo_orders_placed_total"), containsString("mtogo_orders_rejected_total"),
                        containsString("mtogo_orders_unassigned"),
                        containsString("mtogo_order_delivery_time_seconds_count"),
                        containsString("mtogo_order_delivery_time_seconds_sum")));
        assertThat(lines(metrics, "mtogo_order_delivery_time_seconds_bucket"),
                hasItems(containsString("le=\"600.0\""), containsString("le=\"7200.0\"")));
        assertThat(lines(metrics, "http_server_requests_seconds_bucket"),
                hasItem(allOf(containsString("uri=\"/v1/restaurants\""), containsString("le=\"0.5\""))));
        assertThat(lines(metrics, "http_server_requests"), not(hasItem(containsString("uri=\"/actuator"))));
    }

    private void deliverOneOrder()
    {
        long itemId = item(menu(restaurant("Requested", true), "Lunch", true), "Meal", true);
        long orderId = orders
                .place(customer(),
                        new PlaceOrderDTO(List.of(new PlaceOrderDTO.OrderLine(itemId, 1)),
                                new PlaceOrderDTO.DeliveryAddress("Nørregade 10", "1165", "København K"), "tok_visa"))
                .id();
        long agentId = deliveryAgent("agent");
        setOrder(orderId, "PICKED_UP", agentId);
        orders.updateDeliveryAgentOrder(agentId, orderId, new UpdateDeliveryAgentOrderDTO(DeliveryAction.DELIVER));
    }

    private static List<String> lines(String metrics, String prefix)
    {
        return metrics.lines().filter(line -> line.startsWith(prefix)).toList();
    }
}
