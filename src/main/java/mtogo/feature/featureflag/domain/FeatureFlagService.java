package mtogo.feature.featureflag.domain;

import mtogo.feature.featureflag.presentation.dto.response.FeatureFlagsDTO;
import org.springframework.stereotype.Service;

@Service
public class FeatureFlagService
{
    /**
     * Returns every feature flag with the value it had when the backend started. A
     * flag changes only on restart.
     */
    public FeatureFlagsDTO getAll()
    {
        return new FeatureFlagsDTO();
    }
}
