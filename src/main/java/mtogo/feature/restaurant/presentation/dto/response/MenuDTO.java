package mtogo.feature.restaurant.presentation.dto.response;

import java.util.List;

public record MenuDTO(Long id, Long restaurantId, String name, String description, List<MenuItemDTO> items)
{
}
