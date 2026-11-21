package mtogo.feature.order.presentation.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
import mtogo.feature.order.domain.OrderChannel;
import mtogo.feature.order.domain.OrderStatus;

public record OrderDTO(Long id, Long restaurantId, @Schema(description = "Order status code") OrderStatus status,
        @Schema(description = "How the order reached MTOGO") OrderChannel channel, Instant placedAt,
        @Schema(description = "Sum of unit price times quantity over the order lines, in DKK", example = "179.90") BigDecimal total,
        DeliveryAddressDTO deliveryAddress, List<OrderLineDTO> lines)
{
}
