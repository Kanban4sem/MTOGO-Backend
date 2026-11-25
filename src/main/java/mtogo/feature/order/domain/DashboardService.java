package mtogo.feature.order.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mtogo.feature.order.data.OrderRepository;
import mtogo.feature.order.presentation.dto.response.DashboardDTO;
import mtogo.feature.order.presentation.dto.response.DashboardFiguresDTO;
import mtogo.feature.order.presentation.dto.response.RestaurantFiguresDTO;
import mtogo.feature.order.presentation.dto.response.StepMinutesDTO;
import mtogo.feature.restaurant.data.RestaurantRepository;
import mtogo.feature.restaurant.domain.Restaurant;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService
{
    private static final List<OrderStatus> OPEN_STATUSES = Arrays.stream(OrderStatus.values())
            .filter(OrderStatus::isOpen).toList();

    private final OrderRepository orders;
    private final RestaurantRepository restaurants;
    private final Clock clock;

    public DashboardService(OrderRepository orders, RestaurantRepository restaurants, Clock clock)
    {
        this.orders = orders;
        this.restaurants = restaurants;
        this.clock = clock;
    }

    /**
     * Calculates the dashboard for the period from now back exactly {@code days} ×
     * 24 hours, for every restaurant and for all of MTOGO. The total adds up every
     * order itself, so its averages are taken over all orders rather than over the
     * restaurants' averages.
     */
    @Transactional(readOnly = true)
    public DashboardDTO getDashboard(int days)
    {
        // PostgreSQL keeps microseconds, so the edge is compared at that precision.
        Instant until = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Period period = new Period(until.minus(Duration.ofDays(days)), until);
        Figures total = new Figures();
        Map<Long, Figures> byRestaurant = new HashMap<>();
        for (Order order : orders.findAllByStatusInOrChangedBetween(OPEN_STATUSES, period.from(), period.until()))
        {
            total.add(order, period);
            byRestaurant.computeIfAbsent(order.getRestaurant().getId(), id -> new Figures()).add(order, period);
        }
        List<RestaurantFiguresDTO> rows = restaurants.findAll(Sort.by("name", "id")).stream().map(
                restaurant -> toRow(restaurant, byRestaurant.getOrDefault(restaurant.getId(), new Figures()), days))
                .toList();
        return new DashboardDTO(days, total.toDTO(days), rows);
    }

    private static RestaurantFiguresDTO toRow(Restaurant restaurant, Figures figures, int days)
    {
        return new RestaurantFiguresDTO(restaurant.getId(), restaurant.getName(), restaurant.isAcceptingOrders(),
                figures.toDTO(days));
    }

    // An event exactly on either edge is in the period.
    private record Period(Instant from, Instant until)
    {
        boolean contains(Instant time)
        {
            return time != null && !time.isBefore(from) && !time.isAfter(until);
        }
    }

    // Each time on the dashboard runs from the status that starts it to the status
    // that ends it, and counts in the period it ended in.
    private enum Step
    {
        DELIVERY(OrderStatus.PLACED, OrderStatus.DELIVERED), ACCEPTANCE(OrderStatus.PLACED,
                OrderStatus.ACCEPTED), PREPARATION(OrderStatus.ACCEPTED, OrderStatus.READY), PICKUP(OrderStatus.READY,
                        OrderStatus.PICKED_UP), TRANSIT(OrderStatus.PICKED_UP, OrderStatus.DELIVERED);

        final OrderStatus start;
        final OrderStatus end;

        Step(OrderStatus start, OrderStatus end)
        {
            this.start = start;
            this.end = end;
        }
    }

    // The counts, sales and summed times of a set of orders.
    private static final class Figures
    {
        private final Map<OrderStatus, Long> openOrdersByStatus = new EnumMap<>(OrderStatus.class);
        private final Map<Step, TimeSum> times = new EnumMap<>(Step.class);
        private long placedInPeriod;
        private long rejectedInPeriod;
        private BigDecimal sales = BigDecimal.ZERO;

        Figures()
        {
            OPEN_STATUSES.forEach(status -> openOrdersByStatus.put(status, 0L));
            Arrays.stream(Step.values()).forEach(step -> times.put(step, new TimeSum()));
        }

        void add(Order order, Period period)
        {
            if (order.getStatus().isOpen())
            {
                openOrdersByStatus.merge(order.getStatus(), 1L, Long::sum);
            }
            if (period.contains(order.getChangedAt(OrderStatus.PLACED)))
            {
                placedInPeriod++;
            }
            if (period.contains(order.getChangedAt(OrderStatus.REJECTED)))
            {
                rejectedInPeriod++;
            }
            if (period.contains(order.getChangedAt(OrderStatus.DELIVERED)))
            {
                sales = sales.add(order.getTotal());
            }
            for (Step step : Step.values())
            {
                Instant start = order.getChangedAt(step.start);
                Instant end = order.getChangedAt(step.end);
                if (start != null && period.contains(end))
                {
                    times.get(step).add(Duration.between(start, end));
                }
            }
        }

        DashboardFiguresDTO toDTO(int days)
        {
            long openOrders = openOrdersByStatus.values().stream().mapToLong(Long::longValue).sum();
            return new DashboardFiguresDTO(openOrders, new EnumMap<>(openOrdersByStatus),
                    BigDecimal.valueOf(placedInPeriod).divide(BigDecimal.valueOf(days), 1, RoundingMode.HALF_UP),
                    rejectedInPeriod, sales.setScale(2, RoundingMode.HALF_UP), averageMinutes(Step.DELIVERY),
                    new StepMinutesDTO(averageMinutes(Step.ACCEPTANCE), averageMinutes(Step.PREPARATION),
                            averageMinutes(Step.PICKUP), averageMinutes(Step.TRANSIT)));
        }

        private Long averageMinutes(Step step)
        {
            return times.get(step).averageMinutes();
        }
    }

    private static final class TimeSum
    {
        private static final BigDecimal MICROS_PER_MINUTE = BigDecimal.valueOf(60_000_000L);

        private long orders;
        private BigDecimal micros = BigDecimal.ZERO;

        void add(Duration duration)
        {
            orders++;
            micros = micros.add(BigDecimal.valueOf(duration.toNanos() / 1_000));
        }

        // Rounds the exact average, so rounding happens after averaging.
        Long averageMinutes()
        {
            if (orders == 0)
            {
                return null;
            }
            return micros.divide(MICROS_PER_MINUTE.multiply(BigDecimal.valueOf(orders)), 0, RoundingMode.HALF_UP)
                    .longValueExact();
        }
    }
}
