package mtogo.featureflag.domain;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import mtogo.config.AppProperties;
import mtogo.featureflag.presentation.dto.FeatureFlagsDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FeatureFlagServiceTest
{
    @Test
    @DisplayName("Get all - returns each flag with the value it has in the configuration")
    void getAllReturnsEachFlagFromConfiguration()
    {
        assertThat(getAllWithLoginEnabled(true).isLoginEnabled(), is(true));
        assertThat(getAllWithLoginEnabled(false).isLoginEnabled(), is(false));
    }

    // The only place that builds AppProperties, so a new flag changes one line
    // here.
    private static FeatureFlagsDTO getAllWithLoginEnabled(boolean isLoginEnabled)
    {
        AppProperties appProperties = new AppProperties(new AppProperties.Cors("http://localhost:5173"),
                isLoginEnabled);
        return new FeatureFlagService(appProperties).getAll();
    }
}
