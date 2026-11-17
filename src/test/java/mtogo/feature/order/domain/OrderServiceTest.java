package mtogo.feature.order.domain;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import mtogo.exceptions.ApiException;
import mtogo.feature.order.OrderTestData;
import mtogo.feature.order.presentation.dto.request.PlaceOrderDTO;
import mtogo.feature.order.presentation.dto.response.OrderDTO;
import mtogo.feature.order.presentation.dto.response.OrderLineDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"FEATURE_AUTH_ENABLED=false", "FEATURE_ORDERING_ENABLED=true"})
class OrderServiceTest extends OrderTestData
{
    @Autowired
    OrderService orders;

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvFileSource(resources = "/testcases/order/order-service/place-items.csv", numLinesToSkip = 1)
    @DisplayName("Place - orders only known, distinct and orderable menu items from one restaurant")
    void placeChecksMenuItems(String menuItems, String expected, String reason)
    {
        long customerId = customer();
        long restaurantId = restaurant("Requested", !menuItems.equals("notAcceptingOrders"));
        long menuId = menu(restaurantId, "Lunch", !menuItems.equals("inactiveMenu"));
        long itemId = item(menuId, "Meal", !menuItems.equals("unavailable"));
        List<PlaceOrderDTO.OrderLine> lines = switch (menuItems)
        {
            case "unknown" -> List.of(new PlaceOrderDTO.OrderLine(itemId + 1000, 1));
            case "repeated" -> List.of(new PlaceOrderDTO.OrderLine(itemId, 1), new PlaceOrderDTO.OrderLine(itemId, 2));
            case "twoRestaurants" -> {
                long otherMenuId = menu(restaurant("Other", true), "Other lunch", true);
                yield List.of(new PlaceOrderDTO.OrderLine(itemId, 1),
                        new PlaceOrderDTO.OrderLine(item(otherMenuId, "Other meal", true), 1));
            }
            default -> List.of(new PlaceOrderDTO.OrderLine(itemId, 1));
        };
        PlaceOrderDTO request = placeOrder(lines, "tok_visa");

        if (expected.equals("ACCEPT"))
        {
            OrderDTO result = orders.place(customerId, request);
            assertThat(result.restaurantId(), is(restaurantId));
            assertThat(result.lines().stream().map(OrderLineDTO::menuItemId).toList(), contains(itemId));
        } else
        {
            ApiException exception = assertThrows(ApiException.class, () -> orders.place(customerId, request));
            assertThat(exception.getClass().getSimpleName(), is(expected));
            assertThat(orderRows(), is(0L));
        }
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvFileSource(resources = "/testcases/order/order-service/place-payment.csv", numLinesToSkip = 1)
    @DisplayName("Place - saves the order only when the payment is confirmed")
    void placeDependsOnPayment(String paymentToken, String expected, String reason)
    {
        long customerId = customer();
        long itemId = item(menu(restaurant("Requested", true), "Lunch", true), "Meal", true);
        PlaceOrderDTO request = placeOrder(List.of(new PlaceOrderDTO.OrderLine(itemId, 2)), paymentToken);

        if (expected.equals("ACCEPT"))
        {
            OrderDTO result = orders.place(customerId, request);
            assertThat(result.status(), is(OrderStatus.PLACED));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM orders WHERE id = ?", Long.class, result.id()),
                    is(1L));
        } else
        {
            ApiException exception = assertThrows(ApiException.class, () -> orders.place(customerId, request));
            assertThat(exception.getClass().getSimpleName(), is(expected));
            assertThat(exception.getBody().getDetail(), is("The payment was declined"));
            assertThat(orderRows(), is(0L));
        }
    }

    @Test
    @DisplayName("Place - copies each menu item's name and unit price and sums the order total over the order lines")
    void placeCopiesMenuItemsAndSumsTotal()
    {
        long customerId = customer();
        long menuId = menu(restaurant("Requested", true), "Lunch", true);
        long kebabId = item(menuId, "Kebab", "89.95");
        long breadId = item(menuId, "Bread", "25.00");

        OrderDTO result = orders.place(customerId, placeOrder(
                List.of(new PlaceOrderDTO.OrderLine(kebabId, 2), new PlaceOrderDTO.OrderLine(breadId, 3)), "tok_visa"));

        assertThat(result.lines(),
                contains(new OrderLineDTO(kebabId, "Kebab", new BigDecimal("89.95"), 2, new BigDecimal("179.90")),
                        new OrderLineDTO(breadId, "Bread", new BigDecimal("25.00"), 3, new BigDecimal("75.00"))));
        assertThat(result.total(), is(new BigDecimal("254.90")));
        assertThat(jdbc.queryForObject("SELECT total FROM orders WHERE id = ?", BigDecimal.class, result.id()),
                is(new BigDecimal("254.90")));
    }

    @Test
    @DisplayName("Place - a later change to a menu item does not change the placed order")
    void placedOrderKeepsMenuItemAsOrdered()
    {
        long customerId = customer();
        long itemId = item(menu(restaurant("Requested", true), "Lunch", true), "Kebab", "89.95");
        OrderDTO result = orders.place(customerId,
                placeOrder(List.of(new PlaceOrderDTO.OrderLine(itemId, 2)), "tok_visa"));

        jdbc.update("UPDATE menu_items SET name = 'Renamed', price = 120.00 WHERE id = ?", itemId);

        assertThat(jdbc.queryForMap("SELECT name, unit_price FROM order_lines WHERE order_id = ?", result.id()),
                allOf(hasEntry("name", (Object) "Kebab"), hasEntry("unit_price", (Object) new BigDecimal("89.95"))));
        assertThat(jdbc.queryForObject("SELECT total FROM orders WHERE id = ?", BigDecimal.class, result.id()),
                is(new BigDecimal("179.90")));
    }

    @Test
    @DisplayName("Place - records the website channel, the Placed status and when the order was placed")
    void placeRecordsChannelStatusAndTime()
    {
        long customerId = customer();
        long itemId = item(menu(restaurant("Requested", true), "Lunch", true), "Meal", true);
        Instant before = Instant.now().minusSeconds(1);

        OrderDTO result = orders.place(customerId,
                placeOrder(List.of(new PlaceOrderDTO.OrderLine(itemId, 1)), "tok_visa"));

        assertThat(result.channel(), is(OrderChannel.WEBSITE));
        assertThat(result.status(), is(OrderStatus.PLACED));
        assertThat(result.placedAt(), is(both(greaterThan(before)).and(lessThan(Instant.now().plusSeconds(1)))));
        assertThat(jdbc.queryForMap("SELECT channel, status FROM orders WHERE id = ?", result.id()),
                allOf(hasEntry("channel", (Object) "WEBSITE"), hasEntry("status", (Object) "PLACED")));
        var changes = jdbc.queryForList("SELECT status, changed_at FROM order_status_changes WHERE order_id = ?",
                result.id());
        assertThat(changes, hasSize(1));
        assertThat(changes.getFirst().get("status"), is("PLACED"));
        assertThat(((Timestamp) changes.getFirst().get("changed_at")).toInstant(), is(result.placedAt()));
    }

    @Test
    @DisplayName("Place - the order belongs to the customer and the restaurant and is delivered in Denmark")
    void placeRecordsOwnersAndDeliveryAddress()
    {
        long customerId = customer();
        long restaurantId = restaurant("Requested", true);
        long itemId = item(menu(restaurantId, "Lunch", true), "Meal", true);

        OrderDTO result = orders.place(customerId,
                placeOrder(List.of(new PlaceOrderDTO.OrderLine(itemId, 1)), "tok_visa"));

        assertThat(jdbc.queryForMap("SELECT customer_id, restaurant_id FROM orders WHERE id = ?", result.id()),
                allOf(hasEntry("customer_id", (Object) customerId), hasEntry("restaurant_id", (Object) restaurantId)));
        assertThat(result.restaurantId(), is(restaurantId));
        assertThat(result.deliveryAddress().street(), is("Nørregade 10"));
        assertThat(result.deliveryAddress().postalCode(), is("1165"));
        assertThat(result.deliveryAddress().city(), is("København K"));
        assertThat(result.deliveryAddress().country(), is("Denmark"));
        assertThat(jdbc.queryForMap("""
                SELECT a.street, p.code, p.city, p.country FROM orders o
                JOIN addresses a ON a.id = o.delivery_address_id JOIN postal_codes p ON p.id = a.postal_code_id
                WHERE o.id = ?
                """, result.id()), allOf(hasEntry("street", (Object) "Nørregade 10"), hasEntry("code", (Object) "1165"),
                hasEntry("city", (Object) "København K"), hasEntry("country", (Object) "Denmark")));
    }

    private static PlaceOrderDTO placeOrder(List<PlaceOrderDTO.OrderLine> lines, String paymentToken)
    {
        return new PlaceOrderDTO(lines, new PlaceOrderDTO.DeliveryAddress("Nørregade 10", "1165", "København K"),
                paymentToken);
    }
}
