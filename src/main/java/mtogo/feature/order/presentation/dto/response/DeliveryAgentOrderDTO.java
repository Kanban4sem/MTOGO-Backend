package mtogo.feature.order.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import mtogo.feature.order.domain.OrderStatus;

public record DeliveryAgentOrderDTO(Long id, Long restaurantId, String restaurantName, OrderStatus status,
        String statusName, boolean assignedToMe, String deliveryCity,
        @Schema(description = "Delivery address recorded on an unassigned ready order or on the authenticated agent's own order") DeliveryAddressDTO deliveryAddress)
{
}
