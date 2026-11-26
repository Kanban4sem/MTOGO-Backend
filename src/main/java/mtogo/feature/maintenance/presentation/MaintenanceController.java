package mtogo.feature.maintenance.presentation;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import mtogo.feature.maintenance.domain.MaintainableFeature;
import mtogo.feature.maintenance.domain.MaintenanceService;
import mtogo.feature.maintenance.presentation.dto.request.SetMaintenancePauseDTO;
import mtogo.feature.maintenance.presentation.dto.response.MaintenanceStatusDTO;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/maintenance")
@Tag(name = "Maintenance mode", description = "Temporary pauses of released features")
public class MaintenanceController
{
    private final MaintenanceService maintenance;

    public MaintenanceController(MaintenanceService maintenance)
    {
        this.maintenance = maintenance;
    }

    @GetMapping
    @PreAuthorize("permitAll()")
    @Operation(summary = "Read the current pause status of each maintainable feature without signing in")
    public ResponseEntity<List<MaintenanceStatusDTO>> getAll()
    {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(maintenance.getAll());
    }

    @PutMapping("/{feature}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Pause or resume a released feature")
    @ApiResponse(responseCode = "204", description = "Pause status set")
    @ApiResponse(responseCode = "400", description = "The feature or pause state is invalid")
    public void setPause(@PathVariable MaintainableFeature feature, @Valid @RequestBody SetMaintenancePauseDTO request)
    {
        maintenance.setPause(feature, request.isPaused());
    }
}
