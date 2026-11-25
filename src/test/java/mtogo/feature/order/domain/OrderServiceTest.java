package mtogo.feature.order.domain;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import mtogo.exceptions.ApiException;
import mtogo.feature.order.OrderTestData;
import mtogo.feature.order.presentation.dto.request.PlaceOrderDTO;
import mtogo.feature.order.presentation.dto.request.UpdateDeliveryAgentOrderDTO;
import mtogo.feature.order.presentation.dto.request.UpdateRestaurantOrderStatusDTO;
import mtogo.feature.order.presentation.dto.response.CustomerOrderDTO;
import mtogo.feature.order.presentation.dto.response.DeliveryAddressDTO;
import mtogo.feature.order.presentation.dto.response.DeliveryAgentOrderDTO;
import mtogo.feature.order.presentation.dto.response.OrderDTO;
import mtogo.feature.order.presentation.dto.response.OrderLineDTO;
import mtogo.feature.order.presentation.dto.response.RestaurantOrderDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
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

    @ParameterizedTest(name = "[{index}] {6}")
    @CsvFileSource(resources = "/testcases/order/order-service/place-delivery-address.csv", numLinesToSkip = 1)
    @DisplayName("Place - selects and saves the delivery address according to the customer's choice")
    void placeSelectsDeliveryAddress(String profileAddress, boolean hasNewAddress, boolean useDefaultAddress,
            boolean saveAsDefault, String paymentToken, String expected, String reason)
    {
        long customerId = customer();
        String originalStreet = profileAddress.equals("complete") || profileAddress.equals("notDefault")
                ? "Old Street 1"
                : null;
        if (!profileAddress.equals("none"))
        {
            long postalCodeId = jdbc.queryForObject("""
                    INSERT INTO postal_codes (code, city, country) VALUES ('2800', 'Kongens Lyngby', 'Denmark')
                    RETURNING id
                    """, Long.class);
            long addressId = jdbc.queryForObject(
                    "INSERT INTO addresses (street, postal_code_id, is_default) VALUES (?, ?, ?) RETURNING id",
                    Long.class, originalStreet, postalCodeId, !profileAddress.equals("notDefault"));
            jdbc.update(
                    "UPDATE user_profiles SET address_id = ? WHERE id = (SELECT profile_id FROM users WHERE id = ?)",
                    addressId, customerId);
        }
        long rowsBefore = orderRows();
        long itemId = item(menu(restaurant("Requested", true), "Lunch", true), "Meal", true);
        PlaceOrderDTO.DeliveryAddress newAddress = hasNewAddress
                ? new PlaceOrderDTO.DeliveryAddress(" Testvej 67 ", "2900", " Hellerup ")
                : null;
        PlaceOrderDTO request = new PlaceOrderDTO(List.of(new PlaceOrderDTO.OrderLine(itemId, 1)), newAddress,
                paymentToken, useDefaultAddress, saveAsDefault);

        String expectedProfileStreet = originalStreet;
        if (expected.equals("ACCEPT"))
        {
            OrderDTO order = orders.place(customerId, request);
            DeliveryAddressDTO expectedAddress = useDefaultAddress
                    ? new DeliveryAddressDTO("Old Street 1", "2800", "Kongens Lyngby", "Denmark")
                    : new DeliveryAddressDTO("Testvej 67", "2900", "Hellerup", "Denmark");
            assertThat(order.deliveryAddress(), is(expectedAddress));
            if (saveAsDefault)
            {
                expectedProfileStreet = "Testvej 67";
            }
        } else
        {
            ApiException exception = assertThrows(ApiException.class, () -> orders.place(customerId, request));
            assertThat(exception.getClass().getSimpleName(), is(expected));
            assertThat(orderRows(), is(rowsBefore));
        }
        assertThat(jdbc.queryForObject("""
                SELECT a.street FROM users u JOIN user_profiles p ON p.id = u.profile_id
                LEFT JOIN addresses a ON a.id = p.address_id WHERE u.id = ?
                """, String.class, customerId), is(expectedProfileStreet));
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

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvFileSource(resources = "/testcases/order/order-service/get-customer-order-access.csv", numLinesToSkip = 1)
    @DisplayName("Get customer order - a customer can read only their own order")
    void getCustomerOrderChecksOwnership(String order, String expected, String reason)
    {
        long customerId = customer();
        long ownerId = order.equals("OTHER") ? user("other@example.test", "CUSTOMER") : customerId;
        long placedId = order(ownerId, restaurant("Requested", true));
        long orderId = order.equals("UNKNOWN") ? placedId + 1 : placedId;

        if (expected.equals("ACCEPT"))
        {
            CustomerOrderDTO result = orders.getCustomerOrder(customerId, orderId);
            assertThat(result.id(), is(orderId));
        } else
        {
            ApiException exception = assertThrows(ApiException.class,
                    () -> orders.getCustomerOrder(customerId, orderId));
            assertThat(exception.getClass().getSimpleName(), is(expected));
        }
    }

    @ParameterizedTest(name = "[{index}] {4}")
    @CsvFileSource(resources = "/testcases/order/order-service/update-restaurant-order-status-transition.csv", numLinesToSkip = 1)
    @DisplayName("Update restaurant order status - only statuses a restaurant sets, on its own order and allowed by the order lifecycle, are recorded")
    void updateRestaurantOrderStatusFollowsLifecycle(String order, OrderStatus currentStatus,
            OrderStatus requestedStatus, String expected, String reason)
    {
        long restaurantId = restaurant("Requested", true);
        long requesterId = restaurantUser("restaurant@example.test",
                order.equals("OTHER") ? restaurant("Other", true) : restaurantId);
        long placedId = order(customer(), restaurantId);
        setOrder(placedId, currentStatus.name(), null);
        long orderId = order.equals("UNKNOWN") ? placedId + 1 : placedId;
        List<String> previousHistory = orderHistory(placedId);
        UpdateRestaurantOrderStatusDTO request = new UpdateRestaurantOrderStatusDTO(requestedStatus);

        if (expected.equals("ACCEPT"))
        {
            RestaurantOrderDTO result = orders.updateRestaurantOrderStatus(requesterId, orderId, request);
            assertThat(result.status(), is(requestedStatus));
            assertThat(readStatus(orderId), is(requestedStatus.name()));
            assertThat(orderHistory(orderId), is(append(previousHistory, requestedStatus.name())));
        } else
        {
            ApiException exception = assertThrows(ApiException.class,
                    () -> orders.updateRestaurantOrderStatus(requesterId, orderId, request));
            assertThat(exception.getClass().getSimpleName(), is(expected));
            assertThat(readStatus(placedId), is(currentStatus.name()));
            assertThat(orderHistory(placedId), is(previousHistory));
        }
    }

    @ParameterizedTest(name = "[{index}] {4}")
    @CsvFileSource(resources = "/testcases/order/order-service/update-delivery-agent-order-action.csv", numLinesToSkip = 1, nullValues = "NULL")
    @DisplayName("Update delivery agent order - each delivery action depends on the order status and on who has taken the order")
    void updateDeliveryAgentOrderChecksStatusAndAgent(DeliveryAction action, OrderStatus status, String assignedTo,
            String expected, String reason)
    {
        long agentId = deliveryAgent("me");
        long otherAgentId = deliveryAgent("other");
        long placedId = order(customer(), restaurant("Requested", true));
        Long assignedAgentId = assignedAgent(assignedTo, agentId, otherAgentId);
        if (status != null)
        {
            setOrder(placedId, status.name(), assignedAgentId);
        }
        long orderId = status == null ? placedId + 1 : placedId;
        UpdateDeliveryAgentOrderDTO request = new UpdateDeliveryAgentOrderDTO(action);

        if (expected.equals("ACCEPT"))
        {
            DeliveryAgentOrderDTO result = orders.updateDeliveryAgentOrder(agentId, orderId, request);
            OrderStatus expectedStatus = switch (action)
            {
                case TAKE -> OrderStatus.READY;
                case PICK_UP -> OrderStatus.PICKED_UP;
                case DELIVER -> OrderStatus.DELIVERED;
            };
            assertThat(result.status(), is(expectedStatus));
            assertThat(result.isAssignedToMe(), is(true));
            assertThat(readStatus(orderId), is(expectedStatus.name()));
            assertThat(readDeliveryAgent(orderId), is(agentId));
        } else
        {
            String previousStatus = readStatus(placedId);
            ApiException exception = assertThrows(ApiException.class,
                    () -> orders.updateDeliveryAgentOrder(agentId, orderId, request));
            assertThat(exception.getClass().getSimpleName(), is(expected));
            assertThat(readStatus(placedId), is(previousStatus));
            assertThat(readDeliveryAgent(placedId), is(assignedAgentId));
        }
    }

    @ParameterizedTest(name = "[{index}] {3}")
    @CsvFileSource(resources = "/testcases/order/order-service/get-delivery-agent-orders-visibility.csv", numLinesToSkip = 1)
    @DisplayName("Get delivery agent orders - lists ready orders no one has taken and every order the agent has taken")
    void getDeliveryAgentOrdersShowsAvailableAndOwnOrders(OrderStatus status, String assignedTo, String expected,
            String reason)
    {
        long agentId = deliveryAgent("me");
        long otherAgentId = deliveryAgent("other");
        long orderId = order(customer(), restaurant("Requested", true));
        Long assignedAgentId = assignedAgent(assignedTo, agentId, otherAgentId);
        setOrder(orderId, status.name(), assignedAgentId);

        List<Long> listed = orders.getDeliveryAgentOrders(agentId).stream().map(DeliveryAgentOrderDTO::id).toList();

        assertThat(listed.contains(orderId), is(expected.equals("LISTED")));
    }

    private static Long assignedAgent(String assignedTo, long agentId, long otherAgentId)
    {
        return switch (assignedTo)
        {
            case "ME" -> agentId;
            case "OTHER" -> otherAgentId;
            default -> null;
        };
    }

    private static List<String> append(List<String> history, String status)
    {
        return Stream.concat(history.stream(), Stream.of(status)).toList();
    }

    private long order(long customerId, long restaurantId)
    {
        long itemId = item(menu(restaurantId, "Lunch", true), "Meal", true);
        return orders.place(customerId, placeOrder(List.of(new PlaceOrderDTO.OrderLine(itemId, 1)), "tok_visa")).id();
    }

    private static PlaceOrderDTO placeOrder(List<PlaceOrderDTO.OrderLine> lines, String paymentToken)
    {
        return new PlaceOrderDTO(lines, new PlaceOrderDTO.DeliveryAddress("Nørregade 10", "1165", "København K"),
                paymentToken);
    }
}
