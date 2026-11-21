package mtogo.feature.order.presentation.dto.response;

import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.media.Schema;
import mtogo.feature.order.domain.OrderStatus;

public record CustomerOrderSummaryDTO(Long id, Long restaurantId, OrderStatus status,
        @Schema(description = "Current status name in Danish", example = "Bestilt") String statusName,
        @Schema(description = "Order total in DKK", example = "179.90") BigDecimal total)
{
}
