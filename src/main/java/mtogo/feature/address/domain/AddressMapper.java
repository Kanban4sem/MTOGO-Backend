package mtogo.feature.address.domain;

import mtogo.feature.address.presentation.dto.response.AddressDTO;
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
        address.setDefault(true);
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
}
