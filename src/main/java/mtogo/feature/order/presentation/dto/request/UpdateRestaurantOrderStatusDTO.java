package mtogo.feature.order.presentation.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import mtogo.feature.order.domain.OrderStatus;

public record UpdateRestaurantOrderStatusDTO(@NotNull @Schema(allowableValues =
{
        "ACCEPTED", "REJECTED", "READY"}) OrderStatus status){

    @JsonIgnore
    @AssertTrue(message = "must be a status a restaurant sets: ACCEPTED, REJECTED or READY")
    public boolean isSetByRestaurant()
    {
        // A missing status is reported by @NotNull alone.
        return status == null || status.isSetByRestaurant();
    }
}
