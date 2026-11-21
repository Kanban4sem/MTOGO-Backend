package mtogo.feature.order.presentation;

import java.security.Principal;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import mtogo.feature.order.domain.OrderService;
import mtogo.feature.order.presentation.dto.request.UpdateRestaurantOrderStatusDTO;
import mtogo.feature.order.presentation.dto.response.RestaurantOrderDTO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/restaurant/orders")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Restaurant orders", description = "Reading and preparing orders as a restaurant")
public class RestaurantOrderController
{
    private final OrderService orders;

    public RestaurantOrderController(OrderService orders)
    {
        this.orders = orders;
    }

    @GetMapping
    @PreAuthorize("hasRole('RESTAURANT')")
    @Operation(summary = "List the authenticated restaurant's orders, newest first")
    @ApiResponse(responseCode = "404", description = "Ordering is disabled or the account has no restaurant")
    public List<RestaurantOrderDTO> getAll(Principal principal)
    {
        return orders.getRestaurantOrders(Long.valueOf(principal.getName()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('RESTAURANT')")
    @Operation(summary = "Accept or reject a placed order, or mark an accepted order as ready")
    @ApiResponse(responseCode = "400", description = "The order ID or requested status is invalid")
    @ApiResponse(responseCode = "404", description = "The order is unavailable to the restaurant, or ordering is disabled")
    @ApiResponse(responseCode = "409", description = "The order's current status does not allow the requested change")
    public RestaurantOrderDTO updateStatus(@PathVariable Long id,
            @Valid @RequestBody UpdateRestaurantOrderStatusDTO request, Principal principal)
    {
        return orders.updateRestaurantOrderStatus(Long.valueOf(principal.getName()), id, request);
    }
}
