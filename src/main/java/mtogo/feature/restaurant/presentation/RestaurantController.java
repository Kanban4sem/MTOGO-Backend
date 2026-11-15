package mtogo.feature.restaurant.presentation;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import mtogo.feature.restaurant.domain.RestaurantService;
import mtogo.feature.restaurant.presentation.dto.response.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/restaurants")
@Tag(name = "Restaurants", description = "Restaurants accepting orders and their menus")
public class RestaurantController
{
    private final RestaurantService restaurants;

    public RestaurantController(RestaurantService restaurants)
    {
        this.restaurants = restaurants;
    }

    @GetMapping
    @PreAuthorize("permitAll()")
    @Operation(summary = "List restaurants that accept orders")
    public List<RestaurantDTO> getAll()
    {
        return restaurants.getAll();
    }

    @GetMapping("/{restaurantId}/menus")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Get active menus and available items with prices in DKK")
    @ApiResponse(responseCode = "200", description = "Active menus with available items")
    @ApiResponse(responseCode = "404", description = "Restaurant does not exist or does not accept orders")
    public List<MenuDTO> getMenus(@PathVariable Long restaurantId)
    {
        return restaurants.getMenus(restaurantId);
    }
}
