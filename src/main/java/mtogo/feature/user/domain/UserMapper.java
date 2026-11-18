package mtogo.feature.user.domain;

import mtogo.feature.address.domain.AddressMapper;
import mtogo.feature.user.presentation.dto.request.RegisterUserDTO;
import mtogo.feature.user.presentation.dto.response.UserDTO;
import mtogo.feature.user.presentation.dto.response.UserProfileDTO;
import mtogo.feature.user.util.EmailUtil;
import org.springframework.stereotype.Component;

@Component
public class UserMapper
{
    private final AddressMapper addresses;

    public UserMapper(AddressMapper addresses)
    {
        this.addresses = addresses;
    }

    public User toEntity(RegisterUserDTO request)
    {
        User user = new User();
        user.setEmail(EmailUtil.normalize(request.email()));
        UserProfile profile = new UserProfile();
        profile.setFirstName(request.firstName().strip());
        profile.setLastName(request.lastName().strip());
        profile.setPhoneNumber(request.phoneNumber());
        profile.setPreferredLanguage(request.preferredLanguage() == null ? "da" : request.preferredLanguage());
        profile.setAddress(addresses.toEntity(request.postalCode(), request.city(), request.country()));
        user.setProfile(profile);
        return user;
    }

    public UserDTO toDTO(User user)
    {
        UserProfile profile = user.getProfile();
        return new UserDTO(user.getId(), user.getEmail(), user.getRole().getName().name(),
                new UserProfileDTO(profile.getFirstName(), profile.getLastName(), profile.getPhoneNumber(),
                        profile.getPreferredLanguage(), addresses.toDTO(profile.getAddress())));
    }
}
