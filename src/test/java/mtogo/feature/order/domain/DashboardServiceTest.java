package mtogo.feature.order.domain;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import mtogo.feature.order.OrderTestData;
import mtogo.feature.order.presentation.dto.response.DashboardDTO;
import mtogo.feature.order.presentation.dto.response.DashboardFiguresDTO;
import mtogo.feature.order.presentation.dto.response.RestaurantFiguresDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@SpringBootTest(properties = {"FEATURE_AUTH_ENABLED=false"})
class DashboardServiceTest extends OrderTestData
{
    private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");
    private static final List<OrderStatus> DELIVERY_PATH = List.of(OrderStatus.PLACED, OrderStatus.ACCEPTED,
            OrderStatus.READY, OrderStatus.PICKED_UP, OrderStatus.DELIVERED);

    @Autowired
    DashboardService dashboard;

    long customerId;

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

    // Each figure with the step that measures it: the status that starts it and
    // the status that ends it, which decides the period it counts in.
    enum Figure
    {
        ORDERS_PER_24_HOURS(OrderStatus.PLACED, OrderStatus.PLACED,
                DashboardFiguresDTO::ordersPer24Hours), REJECTED_ORDERS(OrderStatus.PLACED, OrderStatus.REJECTED,
                        DashboardFiguresDTO::rejectedOrders), SALES(OrderStatus.PLACED, OrderStatus.DELIVERED,
                                DashboardFiguresDTO::sales), DELIVERY_TIME(OrderStatus.PLACED, OrderStatus.DELIVERED,
                                        DashboardFiguresDTO::averageDeliveryTimeMinutes), ACCEPTANCE_TIME(
                                                OrderStatus.PLACED, OrderStatus.ACCEPTED,
                                                figures -> figures.averageStepMinutes().acceptance()), PREPARATION_TIME(
                                                        OrderStatus.ACCEPTED, OrderStatus.READY,
                                                        figures -> figures.averageStepMinutes()
                                                                .preparation()), PICKUP_TIME(OrderStatus.READY,
                                                                        OrderStatus.PICKED_UP,
                                                                        figures -> figures.averageStepMinutes()
                                                                                .pickup()), TRANSIT_TIME(
                                                                                        OrderStatus.PICKED_UP,
                                                                                        OrderStatus.DELIVERED,
                                                                                        figures -> figures
                                                                                                .averageStepMinutes()
                                                                                                .transit());

        final OrderStatus start;
        final OrderStatus end;
        final Function<DashboardFiguresDTO, Object> value;

        Figure(OrderStatus start, OrderStatus end, Function<DashboardFiguresDTO, Object> value)
        {
            this.start = start;
            this.end = end;
            this.value = value;
        }
    }

    @BeforeEach
    void createCustomer()
    {
        customerId = customer();
    }

    @ParameterizedTest(name = "[{index}] {3}")
    @CsvFileSource(resources = "/testcases/order/dashboard-service/get-dashboard-open-orders.csv", numLinesToSkip = 1)
    @DisplayName("Get dashboard - counts the orders that are open now, in total and per open status")
    void getDashboardCountsOpenOrders(OrderStatus status, int placedDaysAgo, String expected, String reason)
    {
        long restaurantId = restaurant("Bakery", true);
        orderAt(restaurantId, customerId, "100.00", statusesUpTo(status, NOW.minus(Duration.ofDays(placedDaysAgo))));
        boolean isOpen = expected.equals("OPEN");
        Map<OrderStatus, Long> expectedByStatus = openStatuses(0, 0, 0, 0);
        if (isOpen)
        {
            expectedByStatus.put(status, 1L);
        }

        DashboardDTO result = dashboard.getDashboard(7);

        for (DashboardFiguresDTO figures : List.of(result.total(), result.restaurants().getFirst().figures()))
        {
            assertThat(figures.openOrders(), is(isOpen ? 1L : 0L));
            assertThat(figures.openOrdersByStatus(), is(expectedByStatus));
        }
    }

    @ParameterizedTest(name = "[{index}] {4}")
    @CsvFileSource(resources = "/testcases/order/dashboard-service/get-dashboard-window.csv", numLinesToSkip = 1)
    @DisplayName("Get dashboard - counts a figure in the period its step ended in, the edge included")
    void getDashboardCountsStepsEndedInThePeriod(int days, Figure figure, long offsetMicros, String expected,
            String reason)
    {
        long restaurantId = restaurant("Bakery", true);
        Instant stepEnd = NOW.minus(Duration.ofDays(days)).plus(offsetMicros, ChronoUnit.MICROS);
        // As many orders as days, so that counted orders give exactly 1.0 per 24 hours.
        int orderCount = figure == Figure.ORDERS_PER_24_HOURS ? days : 1;
        for (int i = 0; i < orderCount; i++)
        {
            orderAt(restaurantId, customerId, "100.00", stepEndingAt(figure, stepEnd, Duration.ofMinutes(10)));
        }
        boolean isCounted = expected.equals("COUNTED");
        Object expectedValue = switch (figure)
        {
            case ORDERS_PER_24_HOURS -> new BigDecimal(isCounted ? "1.0" : "0.0");
            case REJECTED_ORDERS -> isCounted ? 1L : 0L;
            case SALES -> new BigDecimal(isCounted ? "100.00" : "0.00");
            default -> isCounted ? 10L : null;
        };

        DashboardDTO result = dashboard.getDashboard(days);

        assertThat(figure.value.apply(result.total()), is(expectedValue));
        assertThat(figure.value.apply(result.restaurants().getFirst().figures()), is(expectedValue));
    }

    @ParameterizedTest(name = "[{index}] {4}")
    @CsvFileSource(resources = "/testcases/order/dashboard-service/get-dashboard-rounding.csv", numLinesToSkip = 1)
    @DisplayName("Get dashboard - rounds orders per 24 hours to 1 decimal and average times to whole minutes, half up")
    void getDashboardRoundsHalfUp(Figure figure, int days, String input, String expected, String reason)
    {
        long restaurantId = restaurant("Bakery", true);
        Instant stepEnd = NOW.minus(Duration.ofHours(1));
        if (figure == Figure.ORDERS_PER_24_HOURS)
        {
            for (int i = 0; i < Integer.parseInt(input); i++)
            {
                orderAt(restaurantId, customerId, "100.00", stepEndingAt(figure, stepEnd, Duration.ZERO));
            }
        } else
        {
            for (String seconds : input.split(";"))
            {
                long micros = new BigDecimal(seconds).movePointRight(6).longValueExact();
                orderAt(restaurantId, customerId, "100.00",
                        stepEndingAt(figure, stepEnd, Duration.of(micros, ChronoUnit.MICROS)));
            }
        }
        Object expectedValue = figure == Figure.ORDERS_PER_24_HOURS ? new BigDecimal(expected) : Long.valueOf(expected);

        assertThat(figure.value.apply(dashboard.getDashboard(days).total()), is(expectedValue));
    }

    @Test
    @DisplayName("Get dashboard - without restaurants or orders, every count is 0 and no time has a value")
    void getDashboardWithoutOrders()
    {
        DashboardDTO result = dashboard.getDashboard(7);

        assertThat(result.days(), is(7));
        assertThat(result.restaurants(), is(empty()));
        assertEmptyFigures(result.total());
    }

    @Test
    @DisplayName("Get dashboard - lists a restaurant without orders with every count at 0 and no times")
    void getDashboardListsARestaurantWithoutOrders()
    {
        long busyId = restaurant("Busy", true);
        long quietId = restaurant("Quiet", true);
        orderAt(busyId, customerId, "100.00",
                stepEndingAt(Figure.DELIVERY_TIME, NOW.minus(Duration.ofHours(1)), Duration.ofMinutes(30)));

        DashboardDTO result = dashboard.getDashboard(7);

        assertThat(result.restaurants().stream().map(RestaurantFiguresDTO::id).toList(), contains(busyId, quietId));
        assertEmptyFigures(result.restaurants().get(1).figures());
        assertThat(result.total().averageDeliveryTimeMinutes(), is(30L));
    }

    @Test
    @DisplayName("Get dashboard - a rejected order counts as placed and rejected, but in no time and not in sales")
    void getDashboardCountsARejectedOrderOnlyAsPlacedAndRejected()
    {
        long restaurantId = restaurant("Bakery", true);
        orderAt(restaurantId, customerId, "100.00",
                stepEndingAt(Figure.REJECTED_ORDERS, NOW.minus(Duration.ofHours(1)), Duration.ofMinutes(5)));

        DashboardFiguresDTO total = dashboard.getDashboard(1).total();

        assertThat(total.ordersPer24Hours(), is(new BigDecimal("1.0")));
        assertThat(total.rejectedOrders(), is(1L));
        assertThat(total.openOrders(), is(0L));
        assertThat(total.sales(), is(new BigDecimal("0.00")));
        assertNoTimes(total);
    }

    @Test
    @DisplayName("Get dashboard - an open order counts only in the steps it finished, never in delivery, transit or sales")
    void getDashboardLeavesOpenOrdersOutOfDeliveryAndSales()
    {
        long restaurantId = restaurant("Bakery", true);
        Instant placedAt = NOW.minus(Duration.ofHours(2));
        orderAt(restaurantId, customerId, "100.00", statusesUpTo(OrderStatus.PLACED, placedAt));
        orderAt(restaurantId, customerId, "100.00",
                new EnumMap<>(Map.of(OrderStatus.PLACED, placedAt, OrderStatus.ACCEPTED,
                        placedAt.plus(Duration.ofMinutes(4)), OrderStatus.READY, placedAt.plus(Duration.ofMinutes(22)),
                        OrderStatus.PICKED_UP, placedAt.plus(Duration.ofMinutes(28)))));

        DashboardFiguresDTO total = dashboard.getDashboard(1).total();

        assertThat(total.openOrders(), is(2L));
        assertThat(total.sales(), is(new BigDecimal("0.00")));
        assertThat(total.averageDeliveryTimeMinutes(), is(nullValue()));
        assertThat(total.averageStepMinutes().transit(), is(nullValue()));
        assertThat(total.averageStepMinutes().acceptance(), is(4L));
        assertThat(total.averageStepMinutes().preparation(), is(18L));
        assertThat(total.averageStepMinutes().pickup(), is(6L));
    }

    @Test
    @DisplayName("Get dashboard - the total's counts and sales are the sums of the rows, and its averages are over all orders")
    void getDashboardTotalsTheRestaurants()
    {
        long firstId = restaurant("First", true);
        long secondId = restaurant("Second", true);
        Instant deliveredAt = NOW.minus(Duration.ofHours(1));
        for (int i = 0; i < 3; i++)
        {
            orderAt(firstId, customerId, "100.00",
                    stepEndingAt(Figure.DELIVERY_TIME, deliveredAt, Duration.ofMinutes(10)));
        }
        orderAt(firstId, customerId, "50.00", statusesUpTo(OrderStatus.PLACED, deliveredAt));
        orderAt(secondId, customerId, "250.50",
                stepEndingAt(Figure.DELIVERY_TIME, deliveredAt, Duration.ofMinutes(30)));
        orderAt(secondId, customerId, "80.00", statusesUpTo(OrderStatus.READY, deliveredAt));
        orderAt(secondId, customerId, "80.00",
                stepEndingAt(Figure.REJECTED_ORDERS, deliveredAt, Duration.ofMinutes(2)));

        DashboardDTO result = dashboard.getDashboard(1);

        DashboardFiguresDTO first = result.restaurants().get(0).figures();
        DashboardFiguresDTO second = result.restaurants().get(1).figures();
        DashboardFiguresDTO total = result.total();
        assertThat(total.openOrders(), is(first.openOrders() + second.openOrders()));
        assertThat(total.openOrders(), is(2L));
        assertThat(total.openOrdersByStatus(), is(openStatuses(1, 0, 1, 0)));
        assertThat(total.rejectedOrders(), is(first.rejectedOrders() + second.rejectedOrders()));
        assertThat(total.ordersPer24Hours(), is(new BigDecimal("7.0")));
        assertThat(total.sales(), is(first.sales().add(second.sales())));
        assertThat(total.sales(), is(new BigDecimal("550.50")));
        assertThat(first.averageDeliveryTimeMinutes(), is(10L));
        assertThat(second.averageDeliveryTimeMinutes(), is(30L));
        // (3 × 10 + 30) / 4 orders, not the 20 minutes the rows' averages would give.
        assertThat(total.averageDeliveryTimeMinutes(), is(15L));
    }

    @Test
    @DisplayName("Get dashboard - lists every restaurant by name and shows whether it accepts orders")
    void getDashboardListsRestaurantsByName()
    {
        long zebraId = restaurant("Zebra Pizza", true);
        long alphaId = restaurant("Alpha Sushi", false);
        long middleId = restaurant("Middle Kebab", true);

        List<RestaurantFiguresDTO> rows = dashboard.getDashboard(7).restaurants();

        assertThat(rows.stream().map(RestaurantFiguresDTO::id).toList(), contains(alphaId, middleId, zebraId));
        assertThat(rows.stream().map(RestaurantFiguresDTO::name).toList(),
                contains("Alpha Sushi", "Middle Kebab", "Zebra Pizza"));
        assertThat(rows.stream().map(RestaurantFiguresDTO::isAcceptingOrders).toList(), contains(false, true, true));
    }

    @Test
    @DisplayName("Get dashboard - counts a step that ended in the period for an order placed before it")
    void getDashboardCountsAStepOfAnOrderPlacedBeforeThePeriod()
    {
        long restaurantId = restaurant("Bakery", true);
        Instant placedAt = NOW.minus(Duration.ofDays(10));
        Instant acceptedAt = NOW.minus(Duration.ofDays(8));
        orderAt(restaurantId, customerId, "100.00", new EnumMap<>(Map.of(OrderStatus.PLACED, placedAt,
                OrderStatus.ACCEPTED, acceptedAt, OrderStatus.READY, NOW.minus(Duration.ofHours(1)))));

        DashboardFiguresDTO total = dashboard.getDashboard(7).total();

        assertThat(total.ordersPer24Hours(), is(new BigDecimal("0.0")));
        assertThat(total.averageStepMinutes().acceptance(), is(nullValue()));
        assertThat(total.averageStepMinutes().preparation(), is(Duration.ofDays(8).minusHours(1).toMinutes()));
    }

    private static void assertEmptyFigures(DashboardFiguresDTO figures)
    {
        assertThat(figures.openOrders(), is(0L));
        assertThat(figures.openOrdersByStatus(), is(openStatuses(0, 0, 0, 0)));
        assertThat(figures.ordersPer24Hours(), is(new BigDecimal("0.0")));
        assertThat(figures.rejectedOrders(), is(0L));
        assertThat(figures.sales(), is(new BigDecimal("0.00")));
        assertNoTimes(figures);
    }

    private static void assertNoTimes(DashboardFiguresDTO figures)
    {
        assertThat(figures.averageDeliveryTimeMinutes(), is(nullValue()));
        assertThat(figures.averageStepMinutes().acceptance(), is(nullValue()));
        assertThat(figures.averageStepMinutes().preparation(), is(nullValue()));
        assertThat(figures.averageStepMinutes().pickup(), is(nullValue()));
        assertThat(figures.averageStepMinutes().transit(), is(nullValue()));
    }

    private static Map<OrderStatus, Long> openStatuses(long placed, long accepted, long ready, long pickedUp)
    {
        Map<OrderStatus, Long> statuses = new EnumMap<>(OrderStatus.class);
        statuses.put(OrderStatus.PLACED, placed);
        statuses.put(OrderStatus.ACCEPTED, accepted);
        statuses.put(OrderStatus.READY, ready);
        statuses.put(OrderStatus.PICKED_UP, pickedUp);
        return statuses;
    }

    // Every status up to the given one, all at the same time.
    private static EnumMap<OrderStatus, Instant> statusesUpTo(OrderStatus last, Instant at)
    {
        EnumMap<OrderStatus, Instant> statusTimes = new EnumMap<>(OrderStatus.class);
        statusTimes.put(OrderStatus.PLACED, at);
        if (last == OrderStatus.REJECTED)
        {
            statusTimes.put(OrderStatus.REJECTED, at);
            return statusTimes;
        }
        for (OrderStatus status : DELIVERY_PATH.subList(0, DELIVERY_PATH.indexOf(last) + 1))
        {
            statusTimes.put(status, at);
        }
        return statusTimes;
    }

    // An order whose step for the figure took the duration and ended at stepEnd.
    // The statuses before the step's end all start it.
    private static EnumMap<OrderStatus, Instant> stepEndingAt(Figure figure, Instant stepEnd, Duration duration)
    {
        EnumMap<OrderStatus, Instant> statusTimes = statusesUpTo(figure.end, stepEnd.minus(duration));
        statusTimes.put(figure.end, stepEnd);
        return statusTimes;
    }

}
