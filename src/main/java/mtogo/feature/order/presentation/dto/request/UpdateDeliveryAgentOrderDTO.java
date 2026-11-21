package mtogo.feature.order.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record UpdateDeliveryAgentOrderDTO(@NotNull @Pattern(regexp = "TAKE|PICK_UP|DELIVER") @Schema(allowableValues =
{
        "TAKE", "PICK_UP", "DELIVER"}) String action){
}
