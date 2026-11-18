package mtogo.feature.featureflag.domain;

import mtogo.config.AppProperties;
import mtogo.feature.featureflag.presentation.dto.response.FeatureFlagsDTO;
import org.springframework.stereotype.Service;

@Service
public class FeatureFlagService
{
    private final AppProperties appProperties;

    public FeatureFlagService(AppProperties appProperties)
    {
        this.appProperties = appProperties;
    }

    /**
     * Returns every feature flag with the value it had when the backend started. A
     * flag changes only on restart.
     */
    public FeatureFlagsDTO getAll()
    {
        return new FeatureFlagsDTO(appProperties.isAuthEnabled(), appProperties.isOrderingEnabled());
    }
}
