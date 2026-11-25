package mtogo.feature.order.presentation;

import java.security.Principal;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import mtogo.feature.order.domain.OrderService;
import mtogo.feature.order.presentation.dto.response.CustomerOrderDTO;
import mtogo.feature.order.presentation.dto.response.CustomerOrderSummaryDTO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/customer/orders")
@PreAuthorize("hasRole('CUSTOMER')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Customer orders", description = "Reading orders and their current status as a customer")
public class CustomerOrderController
{
    private final OrderService orders;

    public CustomerOrderController(OrderService orders)
    {
        this.orders = orders;
    }

    @GetMapping
    @Operation(summary = "List the authenticated customer's orders, newest first")
    public List<CustomerOrderSummaryDTO> getAll(Principal principal)
    {
        return orders.getCustomerOrders(Long.valueOf(principal.getName()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one of the authenticated customer's orders with its lines and current status")
    @ApiResponse(responseCode = "400", description = "The order id is not a number")
    @ApiResponse(responseCode = "404", description = "The order is unavailable to the customer")
    public CustomerOrderDTO getById(@PathVariable Long id, Principal principal)
    {
        return orders.getCustomerOrder(Long.valueOf(principal.getName()), id);
    }
}
