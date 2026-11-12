package mtogo.featureflag.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import mtogo.featureflag.domain.IFeatureFlagService;
import mtogo.featureflag.presentation.dto.FeatureFlagsDTO;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/features")
@Tag(name = "Feature flags", description = "Which unfinished features are turned on")
public class FeatureFlagController
{
    private final IFeatureFlagService featureFlagService;

    public FeatureFlagController(IFeatureFlagService featureFlagService)
    {
        this.featureFlagService = featureFlagService;
    }

    // no-store, so a page reload always gets the values the backend started with.
    @GetMapping
    @Operation(summary = "Get every feature flag and whether it is on. Needs no login.")
    public ResponseEntity<FeatureFlagsDTO> getAll()
    {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(featureFlagService.getAll());
    }
}
