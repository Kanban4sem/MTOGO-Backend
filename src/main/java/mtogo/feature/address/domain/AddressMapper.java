package mtogo.feature.address.domain;

import mtogo.feature.address.presentation.dto.response.AddressDTO;
import mtogo.feature.address.presentation.dto.response.SavedDeliveryAddressDTO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AddressMapper
{
    public Address toEntity(String code, String city, String country)
    {
        if (!StringUtils.hasText(code))
        {
            return null;
        }
        PostalCode postalCode = new PostalCode();
        postalCode.setCode(code.strip());
        postalCode.setCity(city.strip());
        postalCode.setCountry(country.strip());
        Address address = new Address();
        address.setPostalCode(postalCode);
        return address;
    }

    public AddressDTO toDTO(Address address)
    {
        if (address == null)
        {
            return null;
        }
        PostalCode postalCode = address.getPostalCode();
        return new AddressDTO(postalCode.getCode(), postalCode.getCity(), postalCode.getCountry(), address.isDefault(),
                address.getStreet());
    }

    public Address toDeliveryAddress(String street, String code, String city)
    {
        Address address = new Address();
        updateDeliveryAddress(address, street, code, city);
        return address;
    }

    public void updateDeliveryAddress(Address address, String street, String code, String city)
    {
        address.changeDeliveryAddress(street.strip(), code, city.strip());
    }

    public SavedDeliveryAddressDTO toSavedDeliveryAddressDTO(Address address)
    {
        PostalCode postalCode = address.getPostalCode();
        return new SavedDeliveryAddressDTO(address.getId(), address.getStreet(), postalCode.getCode(),
                postalCode.getCity(), postalCode.getCountry(), address.isDefault());
    }
}
