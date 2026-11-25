package mtogo.feature.address.domain;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AddressTest
{
    @Test
    @DisplayName("Change delivery address - sets the street, postal code and city, and Denmark as the country")
    void changeDeliveryAddress()
    {
        Address address = new Address();

        address.changeDeliveryAddress("Testvej 67", "2900", "Hellerup");

        assertThat(address.getStreet(), is("Testvej 67"));
        assertThat(address.getPostalCode().getCode(), is("2900"));
        assertThat(address.getPostalCode().getCity(), is("Hellerup"));
        assertThat(address.getPostalCode().getCountry(), is("Denmark"));
    }
}
