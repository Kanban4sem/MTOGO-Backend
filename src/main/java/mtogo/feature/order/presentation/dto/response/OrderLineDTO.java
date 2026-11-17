package mtogo.feature.order.presentation.dto.response;

import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.media.Schema;

public record OrderLineDTO(Long menuItemId,
        @Schema(description = "The menu item's name when the order was placed") String name,
        @Schema(description = "The menu item's price in DKK when the order was placed", example = "89.95") BigDecimal unitPrice,
        int quantity,
        @Schema(description = "Unit price times quantity, in DKK", example = "179.90") BigDecimal subtotal)
{
}
