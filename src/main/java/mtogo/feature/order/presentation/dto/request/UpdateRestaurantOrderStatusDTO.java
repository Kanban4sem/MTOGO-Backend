package mtogo.feature.order.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record UpdateRestaurantOrderStatusDTO(@NotNull @Schema(allowableValues =
{
        "ACCEPTED", "REJECTED", "READY"}) String status){
}
