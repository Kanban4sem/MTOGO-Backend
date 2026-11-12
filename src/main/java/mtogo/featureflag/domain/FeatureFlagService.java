package mtogo.featureflag.domain;

import mtogo.config.AppProperties;
import mtogo.featureflag.presentation.dto.FeatureFlagsDTO;
import org.springframework.stereotype.Service;

@Service
public class FeatureFlagService implements IFeatureFlagService
{
    private final AppProperties appProperties;

    public FeatureFlagService(AppProperties appProperties)
    {
        this.appProperties = appProperties;
    }

    @Override
    public FeatureFlagsDTO getAll()
    {
        return new FeatureFlagsDTO(appProperties.isLoginEnabled());
    }
}
