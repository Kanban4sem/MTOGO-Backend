package mtogo.feature.restaurant.presentation.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

// The JSON field keeps the API name the frontend reads, without the is prefix.
public record MenuItemDTO(Long id, String name, String description, BigDecimal price, String imageUrl,
        @JsonProperty("available") boolean isAvailable)
{
}
