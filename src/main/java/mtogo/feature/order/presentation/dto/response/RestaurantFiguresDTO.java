package mtogo.feature.order.presentation.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

// The figures sit next to the restaurant's fields in JSON, so a row reads like the total.
public record RestaurantFiguresDTO(Long id, String name, @JsonProperty("acceptsOrders") boolean isAcceptingOrders,
        @JsonUnwrapped DashboardFiguresDTO figures)
{
}
