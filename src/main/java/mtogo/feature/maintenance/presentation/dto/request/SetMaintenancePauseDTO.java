package mtogo.feature.maintenance.presentation.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record SetMaintenancePauseDTO(@JsonProperty("paused") @NotNull Boolean isPaused)
{
}
