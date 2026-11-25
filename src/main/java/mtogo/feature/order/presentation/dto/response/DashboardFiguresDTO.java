package mtogo.feature.order.presentation.dto.response;

import java.math.BigDecimal;
import java.util.Map;
import io.swagger.v3.oas.annotations.media.Schema;
import mtogo.feature.order.domain.OrderStatus;

public record DashboardFiguresDTO(
        @Schema(description = "Orders that are open now, whatever the period", example = "12") long openOrders,
        @Schema(description = "Orders that are open now in each of PLACED, ACCEPTED, READY and PICKED_UP, also when 0") Map<OrderStatus, Long> openOrdersByStatus,
        @Schema(description = "Orders placed in the period divided by its days, rejected orders included", example = "34.6") BigDecimal ordersPer24Hours,
        @Schema(description = "Orders rejected in the period", example = "3") long rejectedOrders,
        @Schema(description = "Sum of the totals of the orders delivered in the period, in DKK", example = "18432.50") BigDecimal sales,
        @Schema(description = "Average delivery time of the orders delivered in the period, in whole minutes, or null when none was delivered", example = "45") Long averageDeliveryTimeMinutes,
        @Schema(description = "Average time of each step that ended in the period, in whole minutes") StepMinutesDTO averageStepMinutes)
{
}
