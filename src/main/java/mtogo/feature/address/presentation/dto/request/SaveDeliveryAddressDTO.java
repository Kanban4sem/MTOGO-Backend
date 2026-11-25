package mtogo.feature.address.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SaveDeliveryAddressDTO(@NotBlank @Size(max = 100) String street,
        @NotNull @Pattern(regexp = "\\d{4}") @Schema(description = "Danish postal code", example = "2900") String postalCode,
        @NotBlank @Size(max = 100) String city)
{
}
