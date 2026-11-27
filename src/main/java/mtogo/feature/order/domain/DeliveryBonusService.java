package mtogo.feature.order.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import mtogo.exceptions.BadRequestException;
import mtogo.feature.order.data.BonusDelivery;
import mtogo.feature.order.data.OrderRepository;
import mtogo.feature.order.presentation.dto.response.DeliveryBonusDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryBonusService
{
    private static final ZoneId COPENHAGEN = ZoneId.of("Europe/Copenhagen");
    private static final LocalTime EARLY_UNTIL = LocalTime.of(8, 0);
    private static final LocalTime LATE_FROM = LocalTime.of(22, 0);
    // 0.75% of the order value excluding VAT equals 0.6% of the total at 25% VAT.
    private static final BigDecimal ORDER_TOTAL_RATE = new BigDecimal("0.006");
    private static final BigDecimal COMMISSION_CAP_RATE = new BigDecimal("0.25");
    private static final BigDecimal OFF_HOURS_SUPPLEMENT = new BigDecimal("0.25");
    private static final BigDecimal RATING_FIVE_SUPPLEMENT = new BigDecimal("0.15");
    private static final BigDecimal RATING_SIX_SUPPLEMENT = new BigDecimal("0.30");
    private static final BigDecimal ZERO_DKK = new BigDecimal("0.00");

    private final OrderRepository orders;
    private final Clock clock;

    public DeliveryBonusService(OrderRepository orders, Clock clock)
    {
        this.orders = orders;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DeliveryBonusDTO getBonus(Long agentId, YearMonth requestedMonth)
    {
        YearMonth currentMonth = YearMonth.now(clock.withZone(COPENHAGEN));
        YearMonth month = requestedMonth == null ? currentMonth : requestedMonth;
        if (month.isAfter(currentMonth))
        {
            throw new BadRequestException("The bonus month cannot be in the future");
        }

        Instant from = startOfMonth(month);
        Instant until = startOfMonth(month.plusMonths(1));
        List<BonusDelivery> deliveries = orders.findBonusDeliveries(agentId, from, until);
        BigDecimal bonus = deliveries.stream().map(DeliveryBonusService::calculateBonus).reduce(ZERO_DKK,
                BigDecimal::add);
        return new DeliveryBonusDTO(month.toString(), "DKK", deliveries.size(), bonus);
    }

    private static Instant startOfMonth(YearMonth month)
    {
        return month.atDay(1).atStartOfDay(COPENHAGEN).toInstant();
    }

    private static BigDecimal calculateBonus(BonusDelivery delivery)
    {
        BigDecimal reward = delivery.orderTotal().multiply(ORDER_TOTAL_RATE)
                .add(ratingSupplement(delivery.deliveryRating()));
        LocalTime deliveredAt = delivery.deliveredAt().atZone(COPENHAGEN).toLocalTime();
        if (deliveredAt.isBefore(EARLY_UNTIL) || !deliveredAt.isBefore(LATE_FROM))
        {
            reward = reward.add(OFF_HOURS_SUPPLEMENT);
        }
        BigDecimal cap = delivery.commission().multiply(COMMISSION_CAP_RATE).setScale(2, RoundingMode.DOWN);
        return reward.setScale(2, RoundingMode.HALF_UP).min(cap);
    }

    private static BigDecimal ratingSupplement(Integer rating)
    {
        return switch (rating)
        {
            case 5 -> RATING_FIVE_SUPPLEMENT;
            case 6 -> RATING_SIX_SUPPLEMENT;
            case null, default -> ZERO_DKK;
        };
    }
}
