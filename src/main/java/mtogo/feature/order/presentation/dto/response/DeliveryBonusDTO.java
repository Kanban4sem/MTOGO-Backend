package mtogo.feature.order.presentation.dto.response;

import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.media.Schema;

public record DeliveryBonusDTO(@Schema(description = "Calendar month in Copenhagen", example = "2026-11") String month,
        @Schema(description = "ISO 4217 currency code", example = "DKK") String currency, long deliveredOrders,
        @Schema(description = "Sum of the capped bonuses for the month's deliveries, in DKK", example = "42.75") BigDecimal bonusAmount)
{
}
