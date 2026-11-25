package mtogo.feature.order.presentation;

import java.security.Principal;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import mtogo.feature.order.domain.OrderNotificationService;
import mtogo.feature.order.presentation.dto.response.OrderNotificationDTO;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/customer/orders/notifications")
@PreAuthorize("hasRole('CUSTOMER')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Order notifications", description = "In-app notifications about the authenticated customer's orders")
public class OrderNotificationController
{
    private final OrderNotificationService notifications;

    public OrderNotificationController(OrderNotificationService notifications)
    {
        this.notifications = notifications;
    }

    @GetMapping
    @Operation(summary = "List the customer's order notifications, newest first", description = "Includes read and unread changes after placement. Listing leaves read state unchanged.")
    public List<OrderNotificationDTO> getAll(Principal principal)
    {
        return notifications.getAll(Long.valueOf(principal.getName()));
    }

    @PutMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Mark one of the customer's order notifications as read", description = "Returns 204 also when the notification is already read.")
    @ApiResponse(responseCode = "400", description = "The notification id is not a number")
    @ApiResponse(responseCode = "404", description = "The notification is unavailable to the customer")
    public void markRead(@PathVariable Long id, Principal principal)
    {
        notifications.markRead(Long.valueOf(principal.getName()), id);
    }
}
