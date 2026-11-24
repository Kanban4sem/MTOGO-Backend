package mtogo.feature.order.presentation.dto.response;

import java.math.BigDecimal;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import mtogo.feature.order.domain.OrderStatus;

public record DeliveryAgentOrderDTO(Long id, Long restaurantId, String restaurantName, OrderStatus status,
        @Schema(description = "Current status in Danish and English") StatusNameDTO statusName,
        @JsonProperty("assignedToMe") @Schema(description = "Whether the order is assigned to the authenticated delivery agent") boolean isAssignedToMe,
        @Schema(description = "Delivery address recorded on an unassigned ready order or on the authenticated agent's own order") DeliveryAddressDTO deliveryAddress,
        @Schema(description = "Order total in DKK", example = "179.90") BigDecimal total,
        @Schema(description = "The order lines the delivery agent collects at the restaurant") List<OrderLineDTO> lines)
{
}
