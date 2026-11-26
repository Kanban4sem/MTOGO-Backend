package mtogo.feature.maintenance.presentation.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import mtogo.feature.maintenance.domain.MaintainableFeature;

public record MaintenanceStatusDTO(MaintainableFeature feature, @JsonProperty("paused") boolean isPaused,
        @Schema(description = "Message to display while the feature is paused", example = "Placing orders is temporarily unavailable.") String message)
{
}
