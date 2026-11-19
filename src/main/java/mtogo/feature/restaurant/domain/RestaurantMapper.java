package mtogo.feature.restaurant.domain;

import java.util.List;
import mtogo.feature.restaurant.presentation.dto.response.*;
import org.springframework.stereotype.Component;

@Component
public class RestaurantMapper
{
    public RestaurantDTO toDTO(Restaurant restaurant)
    {
        return new RestaurantDTO(restaurant.getId(), restaurant.getName(), restaurant.isAcceptingOrders());
    }

    public MenuDTO toDTO(Menu menu, List<MenuItem> items)
    {
        return new MenuDTO(menu.getId(), menu.getRestaurant().getId(), menu.getName(), menu.getDescription(),
                items.stream().map(this::toDTO).toList());
    }

    public MenuItemDTO toDTO(MenuItem item)
    {
        return new MenuItemDTO(item.getId(), item.getName(), item.getDescription(), item.getPrice(), item.getImageUrl(),
                item.isAvailable());
    }
}
