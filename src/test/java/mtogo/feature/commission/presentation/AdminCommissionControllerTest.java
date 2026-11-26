package mtogo.feature.commission.presentation;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import mtogo.feature.commission.CommissionTestData;
import mtogo.security.JwtService;
import mtogo.testsupport.AuthenticatedEndpointTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

@AuthenticatedEndpointTest
class AdminCommissionControllerTest extends CommissionTestData
{
    private static final String SCALES_PATH = "/v1/admin/commission-scales";
    private static final String TIERS_PATH = "/v1/admin/commission-scales/tiers";
    private static final String RESTAURANT_SCALE_PATH = "/v1/admin/commission-scales/restaurants/{id}";
    private static final long UNKNOWN_RESTAURANT_ID = Long.MAX_VALUE;

    @LocalServerPort
    int port;
    @Autowired
    JwtService tokens;

    enum Requester
    {
        ANONYMOUS, CUSTOMER, RESTAURANT, DELIVERY_AGENT, ADMIN
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvFileSource(resources = "/testcases/commission/admin-commission-controller/get-access.csv", numLinesToSkip = 1)
    @DisplayName("Get all - only admins see the commission scales")
    void getAccess(Requester role, int expected, String reason)
    {
        assertStatus(requester(role).get(SCALES_PATH), expected);
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvFileSource(resources = "/testcases/commission/admin-commission-controller/put-tiers-access.csv", numLinesToSkip = 1)
    @DisplayName("Replace tiers - only admins replace the sales tiers")
    void putTiersAccess(Requester role, int expected, String reason)
    {
        RequestSpecification request = requester(role).contentType(ContentType.JSON).body(tiersBody());

        assertStatus(request.put(TIERS_PATH), expected);
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvFileSource(resources = "/testcases/commission/admin-commission-controller/put-restaurant-scale-access.csv", numLinesToSkip = 1)
    @DisplayName("Save restaurant scale - only admins give a restaurant its own scale")
    void putRestaurantScaleAccess(Requester role, int expected, String reason)
    {
        long restaurantId = restaurant("Scaled", true);
        RequestSpecification request = requester(role).contentType(ContentType.JSON).body(scaleBody());

        assertStatus(request.put(RESTAURANT_SCALE_PATH, restaurantId), expected);
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvFileSource(resources = "/testcases/commission/admin-commission-controller/delete-restaurant-scale-access.csv", numLinesToSkip = 1)
    @DisplayName("Remove restaurant scale - only admins remove a restaurant's own scale")
    void deleteRestaurantScaleAccess(Requester role, int expected, String reason)
    {
        long restaurantId = restaurant("Scaled", true);

        assertStatus(requester(role).delete(RESTAURANT_SCALE_PATH, restaurantId), expected);
    }

    @ParameterizedTest(name = "[{index}] {3}")
    @CsvFileSource(resources = "/testcases/commission/admin-commission-controller/put-tiers-body.csv", numLinesToSkip = 1, nullValues = "NULL")
    @DisplayName("Replace tiers - takes 1 to 5 tiers, each with a minimum and a scale")
    void putTiersBody(String field, String value, int expected, String reason)
    {
        Map<String, Object> body = tiersBody();
        change(body, field, value);

        Response result = asAdmin().contentType(ContentType.JSON).body(body).put(TIERS_PATH);

        assertStatus(result, expected);
        if (field.equals("NONE"))
        {
            result.then().body("minimumSales", contains(0.0f, 5000.0f));
        }
    }

    @ParameterizedTest(name = "[{index}] {3}")
    @CsvFileSource(resources = "/testcases/commission/admin-commission-controller/put-restaurant-scale-body.csv", numLinesToSkip = 1, nullValues = "NULL")
    @DisplayName("Save restaurant scale - takes a service fee and 1 to 10 brackets within their ranges, with at most 2 decimals")
    void putRestaurantScaleBody(String field, String value, int expected, String reason)
    {
        long restaurantId = restaurant("Scaled", true);
        Map<String, Object> body = scaleBody();
        change(body, field, value);

        Response result = asAdmin().contentType(ContentType.JSON).body(body).put(RESTAURANT_SCALE_PATH, restaurantId);

        assertStatus(result, expected);
        if (field.equals("NONE"))
        {
            result.then().body("restaurantId", is((int) restaurantId)).body("restaurantName", is("Scaled"))
                    .body("scale.serviceFee", is(2.0f)).body("scale.brackets.lowerLimit", contains(0.0f, 100.0f))
                    .body("scale.brackets.ratePercent", contains(4.0f, 2.5f));
        }
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvFileSource(resources = "/testcases/commission/admin-commission-controller/put-restaurant-scale-path.csv", numLinesToSkip = 1)
    @DisplayName("Save restaurant scale - the restaurant must exist")
    void putRestaurantScalePath(String restaurant, int expected, String reason)
    {
        String id = restaurantPathId(restaurant);

        Response result = asAdmin().contentType(ContentType.JSON).body(scaleBody()).put(RESTAURANT_SCALE_PATH, id);

        assertStatus(result, expected);
        if (expected == 404)
        {
            assertThat(restaurantScales(), is(0L));
        }
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvFileSource(resources = "/testcases/commission/admin-commission-controller/delete-restaurant-scale-path.csv", numLinesToSkip = 1)
    @DisplayName("Remove restaurant scale - the restaurant must exist, with or without its own scale")
    void deleteRestaurantScalePath(String restaurant, int expected, String reason)
    {
        long otherId = restaurant("Other", true);
        asAdmin().contentType(ContentType.JSON).body(scaleBody()).put(RESTAURANT_SCALE_PATH, otherId).then()
                .statusCode(200);
        String id = restaurantPathId(restaurant);

        assertStatus(asAdmin().delete(RESTAURANT_SCALE_PATH, id), expected);
        // Only the scale of the restaurant in the path is removed.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commission_scales WHERE restaurant_id = ?", Long.class,
                otherId), is(1L));
        if (restaurant.equals("WITH_OWN_SCALE"))
        {
            assertThat(restaurantScales(), is(1L));
        }
    }

    @Test
    @DisplayName("Get all - returns the sales tiers sorted by minimum and the restaurants' own scales sorted by name")
    void getReturnsTiersAndRestaurantScales()
    {
        asAdmin().contentType(ContentType.JSON).body(tiersBody()).put(TIERS_PATH).then().statusCode(200);
        long zuluId = restaurant("Zulu Pizza", true);
        long alphaId = restaurant("Alpha Sushi", true);
        restaurant("Beta Burger", true);
        for (long restaurantId : List.of(zuluId, alphaId))
        {
            asAdmin().contentType(ContentType.JSON).body(scaleBody()).put(RESTAURANT_SCALE_PATH, restaurantId).then()
                    .statusCode(200);
        }

        Response result = asAdmin().get(SCALES_PATH);

        result.then().statusCode(200).body("tiers.minimumSales", contains(0.0f, 5000.0f))
                .body("tiers.scale.serviceFee", contains(5.0f, 4.0f))
                .body("tiers[0].scale.brackets.lowerLimit", contains(0.0f, 100.0f, 500.0f, 1000.0f))
                .body("tiers[0].scale.brackets.ratePercent", contains(6.0f, 5.0f, 4.0f, 3.0f))
                .body("tiers[1].scale.brackets.lowerLimit", contains(0.0f))
                .body("tiers[1].scale.brackets.ratePercent", contains(3.0f))
                .body("restaurants.restaurantId", contains((int) alphaId, (int) zuluId))
                .body("restaurants.restaurantName", contains("Alpha Sushi", "Zulu Pizza"))
                .body("restaurants.scale.serviceFee", contains(2.0f, 2.0f))
                .body("restaurants[0].scale.brackets.lowerLimit", contains(0.0f, 100.0f))
                .body("restaurants[0].scale.brackets.ratePercent", contains(4.0f, 2.5f));
        // The parsed JSON loses trailing zeros, so the decimal places are checked in
        // the raw body.
        String body = result.asString();
        assertThat(body, allOf(containsString("\"minimumSales\":0.00"), containsString("\"minimumSales\":5000.00"),
                containsString("\"serviceFee\":5.00"), containsString("{\"lowerLimit\":100.00,\"ratePercent\":5.00}"),
                containsString("{\"lowerLimit\":100.00,\"ratePercent\":2.50}")));
    }

    private void assertStatus(Response result, int expected)
    {
        result.then().statusCode(expected);
        if (expected >= 400)
        {
            result.then().contentType("application/problem+json").body("status", is(expected));
        }
    }

    private RequestSpecification requester(Requester role)
    {
        RequestSpecification requester = given().port(port);
        Long userId = switch (role)
        {
            case ANONYMOUS -> null;
            case RESTAURANT -> restaurantUser("requester@example.test", restaurant("Bakery", true));
            default -> user("requester@example.test", role.name());
        };
        return userId == null ? requester : requester.auth().oauth2(tokens.issue(userId, 0).value());
    }

    // Every request in a test is made by the same admin.
    private RequestSpecification asAdmin()
    {
        Long adminId = jdbc.query("SELECT id FROM users WHERE email = 'admin@example.test'",
                rows -> rows.next() ? rows.getLong(1) : null);
        long id = adminId != null ? adminId : user("admin@example.test", "ADMIN");
        return given().port(port).auth().oauth2(tokens.issue(id, 0).value());
    }

    private long restaurantScales()
    {
        return jdbc.queryForObject("SELECT count(*) FROM commission_scales WHERE restaurant_id IS NOT NULL",
                Long.class);
    }

    // The default scale followed by a tier from 5,000 DKK.
    private static Map<String, Object> tiersBody()
    {
        Map<String, Object> body = new HashMap<>();
        body.put("tiers", new ArrayList<>(List.of(tier(0), tier(5))));
        return body;
    }

    // A tier from i thousand DKK. The tier from 0 has the default scale.
    private static Map<String, Object> tier(int i)
    {
        Map<String, Object> tier = new HashMap<>();
        tier.put("minimumSales", i * 1000 + ".00");
        tier.put("scale",
                i == 0
                        ? scale("5.00",
                                List.of(bracket("0.00", "6.00"), bracket("100.00", "5.00"), bracket("500.00", "4.00"),
                                        bracket("1000.00", "3.00")))
                        : scale("4.00", List.of(bracket("0.00", "3.00"))));
        return tier;
    }

    // A scale with a service fee of 2.00 and two brackets.
    private static Map<String, Object> scaleBody()
    {
        return scale("2.00", List.of(bracket("0.00", "4.00"), bracket("100.00", "2.50")));
    }

    private static Map<String, Object> scale(String serviceFee, List<Map<String, Object>> brackets)
    {
        Map<String, Object> scale = new HashMap<>();
        scale.put("serviceFee", serviceFee);
        scale.put("brackets", new ArrayList<>(brackets));
        return scale;
    }

    // Turns the restaurant in a path CSV into the ID for the path. UNKNOWN is an ID
    // no restaurant has, WITHOUT_OWN_SCALE and WITH_OWN_SCALE create a restaurant,
    // and any other value is used as it is.
    private String restaurantPathId(String restaurant)
    {
        return switch (restaurant)
        {
            case "UNKNOWN" -> String.valueOf(UNKNOWN_RESTAURANT_ID);
            case "WITHOUT_OWN_SCALE" -> String.valueOf(restaurant("Scaled", true));
            case "WITH_OWN_SCALE" -> {
                long scaledId = restaurant("Scaled", true);
                asAdmin().contentType(ContentType.JSON).body(scaleBody()).put(RESTAURANT_SCALE_PATH, scaledId).then()
                        .statusCode(200);
                yield String.valueOf(scaledId);
            }
            default -> restaurant;
        };
    }

    // A bracket from i hundred DKK. Equal rates never rise.
    private static Map<String, Object> bracket(int i)
    {
        return bracket(i * 100 + ".00", "4.00");
    }

    private static Map<String, Object> bracket(String lowerLimit, String ratePercent)
    {
        Map<String, Object> bracket = new HashMap<>();
        bracket.put("lowerLimit", lowerLimit);
        bracket.put("ratePercent", ratePercent);
        return bracket;
    }

    // Changes the body as a CSV row says. The field is a path such as
    // "tiers[1].scale.serviceFee". MISSING removes the field, NULL sets it to null,
    // and "<list>.size" fills the list with that many valid elements.
    @SuppressWarnings("unchecked")
    private static void change(Map<String, Object> body, String field, String value)
    {
        if (field.equals("NONE"))
        {
            return;
        }
        Object parent = body;
        String[] steps = field.split("\\.");
        for (int i = 0; i < steps.length - 1; i++)
        {
            parent = step(parent, steps[i]);
        }
        String last = steps[steps.length - 1];
        if (last.equals("size"))
        {
            List<Object> list = (List<Object>) parent;
            list.clear();
            for (int i = 0; i < Integer.parseInt(value); i++)
            {
                list.add(field.startsWith("tiers") ? tier(i) : bracket(i));
            }
        } else if (last.endsWith("]"))
        {
            List<Object> list = (List<Object>) step(parent, last.substring(0, last.indexOf('[')));
            list.set(index(last), value);
        } else if ("MISSING".equals(value))
        {
            ((Map<String, Object>) parent).remove(last);
        } else
        {
            ((Map<String, Object>) parent).put(last, value);
        }
    }

    // Takes one step along a path: "tiers" gives the field, "tiers[1]" an element.
    @SuppressWarnings("unchecked")
    private static Object step(Object parent, String step)
    {
        if (!step.endsWith("]"))
        {
            return ((Map<String, Object>) parent).get(step);
        }
        List<Object> list = (List<Object>) ((Map<String, Object>) parent).get(step.substring(0, step.indexOf('[')));
        return list.get(index(step));
    }

    private static int index(String step)
    {
        return Integer.parseInt(step.substring(step.indexOf('[') + 1, step.length() - 1));
    }
}
