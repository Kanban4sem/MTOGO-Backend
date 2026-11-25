package mtogo.feature.address.presentation.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SavedDeliveryAddressDTO(Long id, String street, String postalCode, String city, String country,
        @JsonProperty("isDefault") boolean isDefault)
{
}
