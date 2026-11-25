package mtogo.feature.address.presentation.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

public record AddressDTO(String postalCode, String city, String country, @JsonProperty("isDefault") boolean isDefault,
        @Schema(description = "Street and house number. A profile with only a postal area has no street.", example = "Testvej 67", nullable = true) String street)
{
}
