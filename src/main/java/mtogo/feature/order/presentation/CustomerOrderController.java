package mtogo.feature.order.presentation;

import java.security.Principal;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import mtogo.feature.order.domain.OrderFeedbackService;
import mtogo.feature.order.domain.OrderService;
import mtogo.feature.order.presentation.dto.request.CreateOrderFeedbackDTO;
import mtogo.feature.order.presentation.dto.response.CustomerOrderDTO;
import mtogo.feature.order.presentation.dto.response.CustomerOrderSummaryDTO;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/customer/orders")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Customer orders", description = "Reading orders and giving feedback as a customer")
public class CustomerOrderController
{
    private final OrderService orders;
    private final OrderFeedbackService feedback;

    public CustomerOrderController(OrderService orders, OrderFeedbackService feedback)
    {
        this.orders = orders;
        this.feedback = feedback;
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "List the authenticated customer's orders, newest first")
    public List<CustomerOrderSummaryDTO> getAll(Principal principal)
    {
        return orders.getCustomerOrders(Long.valueOf(principal.getName()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Get one of the authenticated customer's orders with its lines and current status")
    @ApiResponse(responseCode = "400", description = "The order id is not a number")
    @ApiResponse(responseCode = "404", description = "The order is unavailable to the customer")
    public CustomerOrderDTO getById(@PathVariable Long id, Principal principal)
    {
        return orders.getCustomerOrder(Long.valueOf(principal.getName()), id);
    }

    @PostMapping("/{id}/feedback")
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Give feedback on one of the customer's delivered orders", description = "Requires one rating from 1 to 6 for each of the food, overall experience and delivery agent. An order can receive feedback once.")
    @ApiResponse(responseCode = "201", description = "Feedback submitted")
    @ApiResponse(responseCode = "400", description = "Invalid order id or feedback fields")
    @ApiResponse(responseCode = "404", description = "The order is unavailable to the customer")
    @ApiResponse(responseCode = "409", description = "The order has not been delivered or already has feedback")
    public void createFeedback(@PathVariable Long id, @Valid @RequestBody CreateOrderFeedbackDTO request,
            Principal principal)
    {
        feedback.create(Long.valueOf(principal.getName()), id, request);
    }
}
