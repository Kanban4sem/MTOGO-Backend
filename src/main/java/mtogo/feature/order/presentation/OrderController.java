package mtogo.feature.order.presentation;

import java.net.URI;
import java.security.Principal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import mtogo.feature.order.domain.OrderService;
import mtogo.feature.order.presentation.dto.request.PlaceOrderDTO;
import mtogo.feature.order.presentation.dto.response.OrderDTO;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/orders")
@Tag(name = "Orders", description = "Placing and paying for orders")
public class OrderController
{
    private final OrderService orders;

    public OrderController(OrderService orders)
    {
        this.orders = orders;
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Place an order and pay for it with the mocked payment")
    @ApiResponse(responseCode = "201", description = "Payment confirmed and order placed", headers = @Header(name = HttpHeaders.LOCATION, description = "Customer order URL", schema = @Schema(type = "string", example = "/v1/customer/orders/42")), content = @Content(schema = @Schema(implementation = OrderDTO.class)))
    @ApiResponse(responseCode = "400", description = "Invalid fields or delivery address selection, missing complete default address, unknown or repeated menu items, or menu items from more than one restaurant")
    @ApiResponse(responseCode = "402", description = "The payment was declined. No order was placed")
    @ApiResponse(responseCode = "404", description = "The selected saved delivery address was not found")
    @ApiResponse(responseCode = "409", description = "A menu item is unavailable, on an inactive menu or from a restaurant that does not accept orders")
    public ResponseEntity<OrderDTO> place(@Valid @RequestBody PlaceOrderDTO request, Principal principal)
    {
        OrderDTO order = orders.place(Long.valueOf(principal.getName()), request);
        return ResponseEntity.created(URI.create("/v1/customer/orders/" + order.id())).body(order);
    }
}
