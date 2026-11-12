package mtogo.featureflag.domain;

import mtogo.featureflag.presentation.dto.FeatureFlagsDTO;

public interface IFeatureFlagService
{
    /**
     * Returns every feature flag with the value it had when the backend started. A
     * flag changes only on restart.
     */
    FeatureFlagsDTO getAll();
}
