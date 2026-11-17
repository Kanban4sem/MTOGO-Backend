package mtogo.feature.order.presentation.dto.request;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

// The customer never sends prices, a channel or who they are: the backend takes
// those from the menu, the API and the access token.
public record PlaceOrderDTO(@NotNull @Size(min = 1, max = 20) List<@NotNull @Valid OrderLine> lines,
        @NotNull @Valid DeliveryAddress deliveryAddress,
        @NotBlank @Schema(description = "Token from the payment form. The mocked payment declines the token \"decline\" and confirms any other.", example = "tok_visa") String paymentToken)
{
    public record OrderLine(@NotNull @Positive Long menuItemId,
            @NotNull @Min(1) @Max(20) @Schema(example = "2") Integer quantity)
    {
    }

    // The country is always Denmark, so the customer doesn't send it.
    public record DeliveryAddress(@NotBlank @Size(max = 100) @Schema(example = "Nørregade 10") String street,
            @NotNull @Pattern(regexp = "\\d{4}") @Schema(description = "Danish postal code", example = "1165") String postalCode,
            @NotBlank @Size(max = 100) @Schema(example = "København K") String city)
    {
    }
}
