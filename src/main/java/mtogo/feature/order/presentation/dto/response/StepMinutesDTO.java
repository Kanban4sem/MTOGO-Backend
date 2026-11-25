package mtogo.feature.order.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

// The steps cover different orders, so they need not add up to the delivery time.
public record StepMinutesDTO(
        @Schema(description = "Placed to accepted, or null when no order was accepted in the period", example = "4") Long acceptance,
        @Schema(description = "Accepted to ready, or null when no order became ready in the period", example = "18") Long preparation,
        @Schema(description = "Ready to picked up, or null when no order was picked up in the period", example = "6") Long pickup,
        @Schema(description = "Picked up to delivered, or null when no order was delivered in the period", example = "15") Long transit)
{
}
