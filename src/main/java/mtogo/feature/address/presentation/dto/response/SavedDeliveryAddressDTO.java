package mtogo.feature.address.presentation.dto.response;

public record SavedDeliveryAddressDTO(Long id, String street, String postalCode, String city, String country,
        boolean isDefault)
{
}
