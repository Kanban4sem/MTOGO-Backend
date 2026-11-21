package mtogo.feature.order.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import mtogo.feature.order.domain.OrderStatus;

public record DeliveryAgentOrderDTO(Long id, Long restaurantId, String restaurantName, OrderStatus status,
        String statusName, boolean assignedToMe, String deliveryCity,
        @Schema(description = "Present only for orders assigned to the authenticated delivery agent", nullable = true) DeliveryAddressDTO deliveryAddress)
{
}
