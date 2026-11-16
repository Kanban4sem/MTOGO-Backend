package mtogo.feature.address.domain;

import static org.junit.jupiter.api.Assertions.*;

import mtogo.exceptions.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

class AddressValidatorTest
{
    private final AddressValidator validator = new AddressValidator();

    @ParameterizedTest(name = "[{index}] {4}")
    @CsvFileSource(resources = "/testcases/address/address-validator/validate-completeness.csv", numLinesToSkip = 1, nullValues = "NULL", ignoreLeadingAndTrailingWhitespace = false)
    @DisplayName("Validate - postal code, city and country must be supplied together or all omitted")
    void validateCompleteness(String postalCode, String city, String country, String expected, String reason)
    {
        if (expected.equals("ACCEPT"))
        {
            assertDoesNotThrow(() -> validator.validate(postalCode, city, country));
        } else
        {
            assertThrows(BadRequestException.class, () -> validator.validate(postalCode, city, country));
        }
    }
}
