package mtogo.featureflag.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

// The JSON fields are the flag names the frontend reads, without the is prefix.
public record FeatureFlagsDTO(
        @JsonProperty("authEnabled") @Schema(description = "Whether customers can log in and register. Off until authentication is released.", example = "false") boolean isAuthEnabled)
{
}
