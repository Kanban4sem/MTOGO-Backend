package mtogo.feature.address.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record AddressDTO(String postalCode, String city, String country, boolean isDefault,
        @Schema(description = "Street and house number saved during checkout. A profile with only a postal area has no street.", example = "Testvej 67", nullable = true) String street)
{
}
