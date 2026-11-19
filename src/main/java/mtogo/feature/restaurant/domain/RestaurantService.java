package mtogo.feature.restaurant.domain;

import java.util.List;
import mtogo.exceptions.NotFoundException;
import mtogo.feature.restaurant.data.*;
import mtogo.feature.restaurant.presentation.dto.response.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RestaurantService
{
    private final RestaurantRepository restaurants;
    private final MenuRepository menus;
    private final MenuItemRepository items;
    private final RestaurantMapper mapper;

    public RestaurantService(RestaurantRepository restaurants, MenuRepository menus, MenuItemRepository items,
            RestaurantMapper mapper)
    {
        this.restaurants = restaurants;
        this.menus = menus;
        this.items = items;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<RestaurantDTO> getAll()
    {
        return restaurants.findByIsAcceptingOrdersTrueOrderByIdAsc().stream().map(mapper::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public List<MenuDTO> getMenus(Long restaurantId)
    {
        restaurants.findByIdAndIsAcceptingOrdersTrue(restaurantId)
                .orElseThrow(() -> new NotFoundException("Restaurant not found with id: " + restaurantId));
        return menus.findByRestaurantIdAndIsActiveTrueOrderByIdAsc(restaurantId).stream()
                .map(menu -> mapper.toDTO(menu, items.findByMenuIdAndIsAvailableTrueOrderByIdAsc(menu.getId())))
                .toList();
    }
}
