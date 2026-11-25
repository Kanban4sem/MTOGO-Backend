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
        FeatureFlagsDTO authOnly = getAllWith(true, false);
        assertThat(authOnly.isAuthEnabled(), is(true));
        assertThat(authOnly.isOrderingEnabled(), is(false));
        FeatureFlagsDTO orderingOnly = getAllWith(false, true);
        assertThat(orderingOnly.isAuthEnabled(), is(false));
        assertThat(orderingOnly.isOrderingEnabled(), is(true));
    }

    // The only place that builds AppProperties, so a new flag changes one line
    // here.
    private static FeatureFlagsDTO getAllWith(boolean isAuthEnabled, boolean isOrderingEnabled)
    {
        AppProperties appProperties = new AppProperties(new AppProperties.Cors("http://localhost:5173"), isAuthEnabled,
                isOrderingEnabled, new AppProperties.Seed("", "", "", ""));
        return new FeatureFlagService(appProperties).getAll();
    }
}
