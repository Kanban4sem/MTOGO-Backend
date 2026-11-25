package mtogo.feature.order.presentation;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import mtogo.config.AppProperties;
import mtogo.feature.order.OrderTestData;
import mtogo.feature.user.domain.UserSeedService;
import mtogo.security.JwtService;
import mtogo.testsupport.AuthenticatedEndpointTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

@AuthenticatedEndpointTest
class OrderControllerTest extends OrderTestData
{
    @LocalServerPort
    int port;
    @Autowired
    JwtService tokens;
    @Autowired
    UserSeedService seedUsers;

    @Test
    @DisplayName("Place - a customer receives the order details and can read the placed order at its Location")
    void placeReturnsOrder()
    {
        long restaurantId = restaurant("Requested", true);
        long menuId = menu(restaurantId, "Lunch", true);
        long kebabId = item(menuId, "Kebab", "89.95");
        long breadId = item(menuId, "Bread", "25.00");

        String accessToken = token(customer());
        Response response = given().port(port).auth().oauth2(accessToken).contentType(ContentType.JSON)
                .body(placeOrder(List.of(line(kebabId, 2), line(breadId, 1)))).post("/v1/orders").then().statusCode(201)
                .contentType("application/json").body("id", notNullValue())
                .body("restaurantId", equalTo((int) restaurantId)).body("status", equalTo("PLACED"))
                .body("statusName.danish", equalTo("Bestilt")).body("statusName.english", equalTo("Placed"))
                .body("channel", equalTo("WEBSITE")).body("placedAt", matchesPattern("\\d{4}-\\d{2}-\\d{2}T.+Z"))
                .body("total", equalTo(204.90f)).body("deliveryAddress.street", equalTo("Nørregade 10"))
                .body("deliveryAddress.postalCode", equalTo("1165"))
                .body("deliveryAddress.city", equalTo("København K"))
                .body("deliveryAddress.country", equalTo("Denmark")).body("lines.size()", is(2))
                .body("lines[0].menuItemId", equalTo((int) kebabId)).body("lines[0].name", equalTo("Kebab"))
                .body("lines[0].unitPrice", equalTo(89.95f)).body("lines[0].quantity", is(2))
                .body("lines[0].subtotal", equalTo(179.90f)).body("lines[1].menuItemId", equalTo((int) breadId))
                .extract().response();

        int orderId = response.jsonPath().getInt("id");
        String location = response.getHeader("Location");
        assertThat(location, equalTo("/v1/customer/orders/" + orderId));
        given().port(port).auth().oauth2(accessToken).get(location).then().statusCode(200).body("id", equalTo(orderId))
                .body("status", equalTo("PLACED"));
    }

    @Test
    @DisplayName("Place - a seeded customer can save, reuse and replace a default without changing earlier orders")
    void seededCustomerCanSaveAndReuseDefault()
    {
        AppProperties.Seed seed = new AppProperties.Seed("SeedCustomer123!", null, null, null);
        seedUsers.seed(seed);
        long customerId = jdbc.queryForObject("SELECT id FROM users WHERE email = 'customer@mtogo.example'",
                Long.class);
        String accessToken = token(customerId);
        long itemId = item(menu(restaurant("Requested", true), "Lunch", true), "Meal", true);
        Map<String, Object> reuse = Map.of("lines", List.of(line(itemId, 1)), "paymentToken", "tok_visa",
                "useDefaultAddress", true);

        given().port(port).auth().oauth2(accessToken).contentType(ContentType.JSON).body(reuse).post("/v1/orders")
                .then().statusCode(400)
                .body("detail", equalTo("Your profile has no complete default delivery address"));

        Map<String, Object> save = placeOrder(List.of(line(itemId, 1)));
        save.put("deliveryAddress", Map.of("street", "Testvej 67", "postalCode", "2900", "city", "Hellerup"));
        save.put("saveAsDefault", true);
        long firstOrderId = given().port(port).auth().oauth2(accessToken).contentType(ContentType.JSON).body(save)
                .post("/v1/orders").then().statusCode(201).body("deliveryAddress.street", equalTo("Testvej 67"))
                .extract().jsonPath().getLong("id");

        seedUsers.seed(seed);
        given().port(port).auth().oauth2(accessToken).get("/v1/users/me").then().statusCode(200)
                .body("profile.address.street", equalTo("Testvej 67"))
                .body("profile.address.postalCode", equalTo("2900")).body("profile.address.city", equalTo("Hellerup"))
                .body("profile.address.country", equalTo("Denmark"));

        long reusedOrderId = given().port(port).auth().oauth2(accessToken).contentType(ContentType.JSON).body(reuse)
                .post("/v1/orders").then().statusCode(201).body("deliveryAddress.street", equalTo("Testvej 67"))
                .body("deliveryAddress.postalCode", equalTo("2900")).extract().jsonPath().getLong("id");

        save.put("deliveryAddress",
                Map.of("street", "Second Street 2", "postalCode", "2800", "city", "Kongens Lyngby"));
        given().port(port).auth().oauth2(accessToken).contentType(ContentType.JSON).body(save).post("/v1/orders").then()
                .statusCode(201);
        given().port(port).auth().oauth2(accessToken).contentType(ContentType.JSON).body(reuse).post("/v1/orders")
                .then().statusCode(201).body("deliveryAddress.street", equalTo("Second Street 2"))
                .body("deliveryAddress.postalCode", equalTo("2800"));
        assertThat(jdbc.queryForList("""
                SELECT a.street || ', ' || p.code FROM orders o
                JOIN addresses a ON a.id = o.delivery_address_id JOIN postal_codes p ON p.id = a.postal_code_id
                WHERE o.id IN (?, ?)
                """, String.class, firstOrderId, reusedOrderId), contains("Testvej 67, 2900", "Testvej 67, 2900"));

        String addresses = "/v1/users/me/delivery-addresses";
        long defaultId = given().port(port).auth().oauth2(accessToken).get(addresses).then().statusCode(200)
                .body("[0].isDefault", is(true)).body("[0].street", is("Second Street 2")).extract().jsonPath()
                .getLong("[0].id");
        given().port(port).auth().oauth2(accessToken).contentType(ContentType.JSON)
                .body(Map.of("street", "Edited Street 3", "postalCode", "2900", "city", "Hellerup"))
                .put(addresses + "/" + defaultId).then().statusCode(200);
        given().port(port).auth().oauth2(accessToken).contentType(ContentType.JSON).body(reuse).post("/v1/orders")
                .then().statusCode(201).body("deliveryAddress.street", is("Edited Street 3"));
        given().port(port).auth().oauth2(accessToken).delete(addresses + "/" + defaultId).then().statusCode(204);
        given().port(port).auth().oauth2(accessToken).get("/v1/users/me").then().statusCode(200).body("profile.address",
                nullValue());
        given().port(port).auth().oauth2(accessToken).contentType(ContentType.JSON).body(reuse).post("/v1/orders")
                .then().statusCode(400);
    }

    @ParameterizedTest(name = "[{index}] {5}")
    @CsvFileSource(resources = "/testcases/order/order-controller/place-saved-address.csv", numLinesToSkip = 1)
    @DisplayName("Place - accepts only an owned saved address by itself")
    void placeWithSavedAddress(String selection, boolean hasManualAddress, boolean useDefaultAddress,
            boolean saveAsDefault, int expected, String reason)
    {
        String customerToken = token(customer());
        String otherToken = token(user("other@example.test", "CUSTOMER"));
        long itemId = item(menu(restaurant("Requested", true), "Lunch", true), "Meal", true);
        String collection = "/v1/users/me/delivery-addresses";
        Map<String, String> address = Map.of("street", "Saved Street 10", "postalCode", "2900", "city", "Hellerup");
        long addressId = given().port(port).auth().oauth2(selection.equals("other") ? otherToken : customerToken)
                .contentType(ContentType.JSON).body(address).post(collection).then().statusCode(201).extract()
                .jsonPath().getLong("id");
        long selectedId = switch (selection)
        {
            case "missing" -> addressId + 1000;
            case "zero" -> 0;
            case "negative" -> -1;
            default -> addressId;
        };
        Map<String, Object> request = placeOrder(List.of(line(itemId, 1)));
        if (!hasManualAddress)
        {
            request.remove("deliveryAddress");
        }
        request.put("deliveryAddressId", selectedId);
        request.put("useDefaultAddress", useDefaultAddress);
        request.put("saveAsDefault", saveAsDefault);
        var result = given().port(port).auth().oauth2(customerToken).contentType(ContentType.JSON).body(request)
                .post("/v1/orders").then().statusCode(expected);
        if (expected == 201)
        {
            String location = result.body("deliveryAddress.street", is("Saved Street 10")).extract().header("Location");
            given().port(port).auth().oauth2(customerToken).contentType(ContentType.JSON)
                    .body(Map.of("street", "Changed Street 11", "postalCode", "2800", "city", "Lyngby"))
                    .put(collection + "/" + addressId).then().statusCode(200);
            given().port(port).auth().oauth2(customerToken).delete(collection + "/" + addressId).then().statusCode(204);
            given().port(port).auth().oauth2(customerToken).get(location).then().statusCode(200)
                    .body("deliveryAddress.street", is("Saved Street 10"))
                    .body("deliveryAddress.postalCode", is("2900"));
        } else
        {
            result.contentType("application/problem+json").body("status", is(expected));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Long.class), is(0L));
        }
    }

    @ParameterizedTest(name = "[{index}] {3}")
    @CsvFileSource(resources = "/testcases/order/order-controller/place-fields.csv", numLinesToSkip = 1, nullValues = "NULL")
    @DisplayName("Place - validates the order lines, delivery address and payment token")
    void placeValidatesFields(String field, String value, int expected, String reason)
    {
        long menuId = menu(restaurant("Requested", true), "Lunch", true);
        long itemId = item(menuId, "Meal", true);
        Map<String, Object> request = placeOrder(List.of(line(itemId, 1)));
        @SuppressWarnings("unchecked")
        Map<String, Object> firstLine = ((List<Map<String, Object>>) request.get("lines")).getFirst();
        @SuppressWarnings("unchecked")
        Map<String, Object> address = (Map<String, Object>) request.get("deliveryAddress");
        switch (field)
        {
            case "lineCount" -> {
                List<Map<String, Object>> lines = new ArrayList<>();
                for (int i = 0; i < Integer.parseInt(value); i++)
                {
                    lines.add(line(item(menuId, "Meal " + i, true), 1));
                }
                request.put("lines", lines);
            }
            case "lines", "deliveryAddress", "paymentToken" -> request.put(field, value);
            case "line" -> request.put("lines", new ArrayList<>(java.util.Collections.singletonList(null)));
            case "menuItemId" -> firstLine.put(field, value);
            case "quantity" -> firstLine.put(field, value == null ? null : Integer.parseInt(value));
            case "streetLength", "cityLength" ->
                address.put(field.replace("Length", ""), "a".repeat(Integer.parseInt(value)));
            default -> address.put(field, value);
        }

        given().port(port).auth().oauth2(token(customer())).contentType(ContentType.JSON).body(request)
                .post("/v1/orders").then().statusCode(expected);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Long.class), is(expected == 201 ? 1L : 0L));
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvFileSource(resources = "/testcases/order/order-controller/place-access.csv", numLinesToSkip = 1)
    @DisplayName("Place - only customers can place orders")
    void placeIsForCustomersOnly(String role, int expected, String reason)
    {
        long itemId = item(menu(restaurant("Requested", true), "Lunch", true), "Meal", true);
        RequestSpecification request = given().port(port).contentType(ContentType.JSON)
                .body(placeOrder(List.of(line(itemId, 1))));
        if (!role.equals("ANONYMOUS"))
        {
            request.auth().oauth2(token(user(role.toLowerCase() + "@example.test", role)));
        }

        request.post("/v1/orders").then().statusCode(expected);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Long.class), is(expected == 201 ? 1L : 0L));
    }

    @Test
    @DisplayName("Place - an unknown menu item is a 400 ProblemDetail")
    void unknownMenuItemIsBadRequest()
    {
        long itemId = item(menu(restaurant("Requested", true), "Lunch", true), "Meal", true);

        given().port(port).auth().oauth2(token(customer())).contentType(ContentType.JSON)
                .body(placeOrder(List.of(line(itemId + 1000, 1)))).post("/v1/orders").then().statusCode(400)
                .contentType("application/problem+json").body("status", is(400))
                .body("detail", equalTo("Menu item not found with id: " + (itemId + 1000)));
    }

    @Test
    @DisplayName("Place - a declined payment is a 402 ProblemDetail telling the customer the payment was declined")
    void declinedPaymentIsPaymentRequired()
    {
        long itemId = item(menu(restaurant("Requested", true), "Lunch", true), "Meal", true);
        Map<String, Object> request = placeOrder(List.of(line(itemId, 1)));
        request.put("paymentToken", "decline");

        given().port(port).auth().oauth2(token(customer())).contentType(ContentType.JSON).body(request)
                .post("/v1/orders").then().statusCode(402).contentType("application/problem+json")
                .body("status", is(402)).body("detail", equalTo("The payment was declined"));
    }

    @Test
    @DisplayName("Place - an unavailable menu item is a 409 ProblemDetail")
    void unavailableMenuItemIsConflict()
    {
        long itemId = item(menu(restaurant("Requested", true), "Lunch", true), "Meal", false);

        given().port(port).auth().oauth2(token(customer())).contentType(ContentType.JSON)
                .body(placeOrder(List.of(line(itemId, 1)))).post("/v1/orders").then().statusCode(409)
                .contentType("application/problem+json").body("status", is(409))
                .body("detail", equalTo("Menu item is not available: Meal"));
    }

    private String token(long userId)
    {
        return tokens.issue(userId, 0).value();
    }
}
