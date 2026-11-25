package mtogo.feature.user.domain;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import mtogo.exceptions.NotFoundException;
import mtogo.exceptions.UnauthorizedException;
import mtogo.feature.address.domain.Address;
import mtogo.feature.address.domain.AddressMapper;
import mtogo.feature.address.presentation.dto.request.SaveDeliveryAddressDTO;
import mtogo.feature.address.presentation.dto.response.SavedDeliveryAddressDTO;
import mtogo.feature.user.data.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CustomerDeliveryAddressService
{
    private final UserRepository users;
    private final AddressMapper addresses;

    public CustomerDeliveryAddressService(UserRepository users, AddressMapper addresses)
    {
        this.users = users;
        this.addresses = addresses;
    }

    @Transactional(readOnly = true)
    public List<SavedDeliveryAddressDTO> getAll(Long customerId)
    {
        UserProfile profile = activeProfile(users.findById(customerId));
        return savedAddresses(profile).map(addresses::toSavedDeliveryAddressDTO).toList();
    }

    @Transactional(readOnly = true)
    public SavedDeliveryAddressDTO get(Long customerId, Long addressId)
    {
        UserProfile profile = activeProfile(users.findById(customerId));
        return addresses.toSavedDeliveryAddressDTO(requireAddress(profile, addressId));
    }

    @Transactional
    public SavedDeliveryAddressDTO create(Long customerId, SaveDeliveryAddressDTO request)
    {
        UserProfile profile = activeProfile(users.findByIdForUpdate(customerId));
        Address address = addresses.toDeliveryAddress(request.street(), request.postalCode(), request.city());
        profile.getSavedDeliveryAddresses().add(address);
        users.flush();
        return addresses.toSavedDeliveryAddressDTO(address);
    }

    @Transactional
    public SavedDeliveryAddressDTO update(Long customerId, Long addressId, SaveDeliveryAddressDTO request)
    {
        UserProfile profile = activeProfile(users.findByIdForUpdate(customerId));
        Address address = requireAddress(profile, addressId);
        addresses.updateDeliveryAddress(address, request.street(), request.postalCode(), request.city());
        return addresses.toSavedDeliveryAddressDTO(address);
    }

    @Transactional
    public void delete(Long customerId, Long addressId)
    {
        UserProfile profile = activeProfile(users.findByIdForUpdate(customerId));
        Address address = requireAddress(profile, addressId);
        if (address.isDefault())
        {
            profile.setAddress(null);
        } else
        {
            profile.getSavedDeliveryAddresses().remove(address);
        }
    }

    private static UserProfile activeProfile(Optional<User> user)
    {
        return user.filter(User::isActive).orElseThrow(() -> new UnauthorizedException("Invalid access token"))
                .getProfile();
    }

    private static Address requireAddress(UserProfile profile, Long addressId)
    {
        return savedAddresses(profile).filter(address -> address.getId().equals(addressId)).findFirst()
                .orElseThrow(() -> new NotFoundException("Saved delivery address not found with id: " + addressId));
    }

    private static Stream<Address> savedAddresses(UserProfile profile)
    {
        Stream<Address> completeDefault = Stream.ofNullable(profile.getAddress())
                .filter(address -> address.isDefault() && StringUtils.hasText(address.getStreet()));
        return Stream.concat(completeDefault, profile.getSavedDeliveryAddresses().stream());
    }
}
