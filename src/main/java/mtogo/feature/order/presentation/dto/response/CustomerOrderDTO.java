package mtogo.feature.order.presentation.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
import mtogo.feature.order.domain.OrderStatus;

public record CustomerOrderDTO(Long id, Long restaurantId, OrderStatus status,
        @Schema(description = "Current status in Danish and English") StatusNameDTO statusName, Instant placedAt,
        @Schema(description = "Order total in DKK", example = "179.90") BigDecimal total,
        @Schema(description = "Delivery address recorded on the order, not the customer's current default") DeliveryAddressDTO deliveryAddress,
        List<OrderLineDTO> lines)
{
}
