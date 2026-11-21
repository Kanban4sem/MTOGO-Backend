package mtogo.feature.order.presentation;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.util.List;
import java.util.Map;
import io.restassured.http.ContentType;
import mtogo.testsupport.SharedDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SharedDatabase
@ActiveProfiles("local")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"FEATURE_AUTH_ENABLED=true",
        "FEATURE_ORDERING_ENABLED=true", "JWT_SECRET=0123456789abcdef0123456789abcdef0123456789abcdef",
        "app.seed.customer-password=Customer123!"})
class OrderConflictSeedTest
{
    @LocalServerPort
    int port;
    @Autowired
    JdbcTemplate jdbc;

    @ParameterizedTest(name = "[{index}] {3}")
    @CsvFileSource(resources = "/testcases/order/order-controller/place-seeded-conflicts.csv", numLinesToSkip = 1)
    @DisplayName("Place - local seed data exercises each order conflict")
    void placeSeededConflict(String itemName, String detail, int expected, String reason)
    {
        long itemId = jdbc.queryForObject("SELECT id FROM menu_items WHERE name = ?", Long.class, itemName);
        String token = given().port(port).contentType(ContentType.JSON)
                .body(Map.of("email", "customer@mtogo.example", "password", "Customer123!")).post("/v1/users/login")
                .then().statusCode(200).extract().path("accessToken");

        given().port(port).auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of("lines", List.of(Map.of("menuItemId", itemId, "quantity", 1)), "deliveryAddress",
                        Map.of("street", "Test Street 10", "postalCode", "1165", "city", "Copenhagen", "country",
                                "Denmark"),
                        "paymentToken", "tok_visa"))
                .post("/v1/orders").then().statusCode(expected).contentType("application/problem+json")
                .body("detail", equalTo(detail));
    }
}
