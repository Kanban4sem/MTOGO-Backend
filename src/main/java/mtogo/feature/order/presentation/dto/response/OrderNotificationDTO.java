package mtogo.feature.order.presentation.dto.response;

import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;
import mtogo.feature.order.domain.OrderStatus;

public record OrderNotificationDTO(
        @Schema(description = "Notification ID used to mark this status change as read", example = "42") Long id,
        Long orderId, @Schema(description = "The status reached by this change", example = "READY") OrderStatus status,
        StatusNameDTO statusName, Instant changedAt, boolean read)
{
}
