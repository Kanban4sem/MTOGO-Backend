package mtogo.feature.user.domain;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import mtogo.feature.address.domain.Address;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

class UserProfileTest
{
    @ParameterizedTest(name = "[{index}] {2}")
    @CsvFileSource(resources = "/testcases/user/user-profile/set-preferred-language.csv", numLinesToSkip = 1, nullValues = "NULL")
    @DisplayName("Set preferred language - Danish when no language is given")
    void setPreferredLanguage(String preferredLanguage, String expected, String reason)
    {
        UserProfile profile = new UserProfile();

        profile.setPreferredLanguage(preferredLanguage);

        assertThat(profile.getPreferredLanguage(), is(expected));
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvFileSource(resources = "/testcases/user/user-profile/set-address.csv", numLinesToSkip = 1, nullValues = "NULL")
    @DisplayName("Set address - the profile address is always the default address")
    void setAddress(String address, Boolean expectedDefault, String reason)
    {
        UserProfile profile = new UserProfile();
        Address given = null;
        if (address != null)
        {
            given = new Address();
            given.setDefault(address.equals("DEFAULT"));
        }

        profile.setAddress(given);

        if (expectedDefault == null)
        {
            assertThat(profile.getAddress(), is(nullValue()));
        } else
        {
            assertThat(profile.getAddress(), is(sameInstance(given)));
            assertThat(profile.getAddress().isDefault(), is(expectedDefault));
        }
    }
}
