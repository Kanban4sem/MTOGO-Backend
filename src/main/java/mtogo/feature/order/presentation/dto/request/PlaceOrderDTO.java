package mtogo.feature.order.presentation.dto.request;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

// The customer never sends prices, a channel or who they are: the backend takes
// those from the menu, the API and the access token.
public record PlaceOrderDTO(@NotNull @Size(min = 1, max = 20) List<@NotNull @Valid OrderLine> lines,
        @Valid @Schema(description = "Required when useDefaultAddress is false. Omit when using the saved address.") DeliveryAddress deliveryAddress,
        @NotBlank @Schema(description = "Token from the payment form. The mocked payment declines the token \"decline\" and confirms any other.", example = "tok_visa") String paymentToken,
        @Schema(description = "Use the complete delivery address saved on the customer's profile.", defaultValue = "false") Boolean useDefaultAddress,
        @Schema(description = "Save the entered deliveryAddress as the profile default after successful payment.", defaultValue = "false") Boolean saveAsDefault)
{
    public PlaceOrderDTO {
        useDefaultAddress = Boolean.TRUE.equals(useDefaultAddress);
        saveAsDefault = Boolean.TRUE.equals(saveAsDefault);
    }

    public PlaceOrderDTO(List<OrderLine> lines, DeliveryAddress deliveryAddress, String paymentToken)
    {
        this(lines, deliveryAddress, paymentToken, false, false);
    }

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
