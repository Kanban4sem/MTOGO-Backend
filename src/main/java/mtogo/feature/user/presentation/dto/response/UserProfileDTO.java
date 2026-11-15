package mtogo.feature.user.presentation.dto.response;

import mtogo.feature.address.presentation.dto.response.AddressDTO;
public record UserProfileDTO(String firstName, String lastName, String phoneNumber, String preferredLanguage,
        AddressDTO address)
{
}
