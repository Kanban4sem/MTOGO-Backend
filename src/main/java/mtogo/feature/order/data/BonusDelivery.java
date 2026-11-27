package mtogo.feature.order.data;

import java.math.BigDecimal;
import java.time.Instant;

public record BonusDelivery(BigDecimal orderTotal, BigDecimal commission, Instant deliveredAt, Integer deliveryRating)
{
}
