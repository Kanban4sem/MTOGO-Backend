package mtogo.feature.featureflag.domain;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import mtogo.feature.featureflag.presentation.dto.response.FeatureFlagsDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FeatureFlagServiceTest
{
    @Test
    @DisplayName("Get all - returns no flags while none is declared")
    void getAllReturnsNoFlags()
    {
        assertThat(new FeatureFlagService().getAll(), is(new FeatureFlagsDTO()));
    }
}
