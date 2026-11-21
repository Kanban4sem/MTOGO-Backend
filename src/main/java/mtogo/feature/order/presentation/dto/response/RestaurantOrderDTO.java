package mtogo.feature.order.presentation.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
import mtogo.feature.order.domain.OrderStatus;

public record RestaurantOrderDTO(Long id, OrderStatus status,
        @Schema(description = "Current status name in Danish", example = "Bestilt") String statusName, Instant placedAt,
        @Schema(description = "Order total in DKK", example = "179.90") BigDecimal total, List<OrderLineDTO> lines)
{
}
