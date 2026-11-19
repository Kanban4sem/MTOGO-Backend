package mtogo.feature.restaurant.presentation.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

// The JSON field keeps the API name the frontend reads, matching the accepts_orders column.
public record RestaurantDTO(Long id, String name, @JsonProperty("acceptsOrders") boolean isAcceptingOrders)
{
}
