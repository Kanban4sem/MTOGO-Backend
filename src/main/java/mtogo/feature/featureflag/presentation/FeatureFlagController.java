package mtogo.feature.featureflag.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import mtogo.feature.featureflag.domain.FeatureFlagService;
import mtogo.feature.featureflag.presentation.dto.response.FeatureFlagsDTO;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/features")
@Tag(name = "Feature flags", description = "Which unfinished features are turned on")
public class FeatureFlagController
{
    private final FeatureFlagService featureFlagService;

    public FeatureFlagController(FeatureFlagService featureFlagService)
    {
        this.featureFlagService = featureFlagService;
    }

    // no-store, so a page reload always gets the values the backend started with.
    @GetMapping
    @PreAuthorize("permitAll()")
    @Operation(summary = "Get every feature flag and whether it is on. Needs no login.")
    public ResponseEntity<FeatureFlagsDTO> getAll()
    {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(featureFlagService.getAll());
    }
}
