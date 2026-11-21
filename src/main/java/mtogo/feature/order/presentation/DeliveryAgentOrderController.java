package mtogo.feature.order.presentation;

import java.security.Principal;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import mtogo.feature.order.domain.OrderService;
import mtogo.feature.order.presentation.dto.request.UpdateDeliveryAgentOrderDTO;
import mtogo.feature.order.presentation.dto.response.DeliveryAgentOrderDTO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/delivery-agent/orders")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Delivery agent orders", description = "Taking, collecting and delivering orders")
public class DeliveryAgentOrderController
{
    private final OrderService orders;

    public DeliveryAgentOrderController(OrderService orders)
    {
        this.orders = orders;
    }

    @GetMapping
    @PreAuthorize("hasRole('DELIVERY_AGENT')")
    @Operation(summary = "List unassigned ready orders and the authenticated agent's own orders, newest first")
    @ApiResponse(responseCode = "404", description = "Ordering is disabled")
    public List<DeliveryAgentOrderDTO> getAll(Principal principal)
    {
        return orders.getDeliveryAgentOrders(Long.valueOf(principal.getName()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('DELIVERY_AGENT')")
    @Operation(summary = "Take a ready order, or mark an assigned order as picked up or delivered")
    @ApiResponse(responseCode = "400", description = "The order ID or action is invalid")
    @ApiResponse(responseCode = "404", description = "The order is unavailable to the agent, or ordering is disabled")
    @ApiResponse(responseCode = "409", description = "The order is already assigned or its status prevents the action")
    public DeliveryAgentOrderDTO update(@PathVariable Long id, @Valid @RequestBody UpdateDeliveryAgentOrderDTO request,
            Principal principal)
    {
        return orders.updateDeliveryAgentOrder(Long.valueOf(principal.getName()), id, request);
    }
}
