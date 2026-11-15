package mtogo.feature.restaurant.presentation.dto.response;

import java.math.BigDecimal;

public record MenuItemDTO(Long id, String name, String description, BigDecimal price, String imageUrl,
        boolean available)
{
}
