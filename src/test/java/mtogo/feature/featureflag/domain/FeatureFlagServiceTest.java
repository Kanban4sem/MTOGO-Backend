package mtogo.feature.featureflag.domain;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import mtogo.config.AppProperties;
import mtogo.feature.featureflag.presentation.dto.response.FeatureFlagsDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FeatureFlagServiceTest
{
    @Test
    @DisplayName("Get all - returns each flag with the value it has in the configuration")
    void getAllReturnsEachFlagFromConfiguration()
    {
        assertThat(getAllWith(true).isAuthEnabled(), is(true));
        assertThat(getAllWith(false).isAuthEnabled(), is(false));
    }

    // The only place that builds AppProperties, so a new flag changes one line
    // here.
    private static FeatureFlagsDTO getAllWith(boolean isAuthEnabled)
    {
        AppProperties appProperties = new AppProperties(new AppProperties.Cors("http://localhost:5173"), isAuthEnabled,
                new AppProperties.Seed("", "", "", ""));
        return new FeatureFlagService(appProperties).getAll();
    }
}
