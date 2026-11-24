package mtogo.feature.order.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import mtogo.feature.order.domain.DeliveryAction;

public record UpdateDeliveryAgentOrderDTO(@NotNull DeliveryAction action)
{
}
