package mtogo.feature.address.domain;

import mtogo.exceptions.BadRequestException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AddressValidator
{
    public void validate(String postalCode, String city, String country)
    {
        boolean hasPostalCode = StringUtils.hasText(postalCode);
        boolean hasCity = StringUtils.hasText(city);
        boolean hasCountry = StringUtils.hasText(country);
        if ((hasPostalCode || hasCity || hasCountry) && !(hasPostalCode && hasCity && hasCountry))
        {
            throw new BadRequestException("Postal code, city and country must be supplied together");
        }
    }
}
