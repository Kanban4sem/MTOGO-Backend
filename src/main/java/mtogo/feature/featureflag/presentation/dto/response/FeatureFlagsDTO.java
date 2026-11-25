package mtogo.feature.featureflag.presentation.dto.response;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

// The JSON fields are the flag names the frontend reads, without the is prefix:
// @JsonProperty("exampleEnabled") boolean isExampleEnabled. With no flags the body is
// {}, and @JsonAutoDetect lets Jackson serialize the empty record.
@JsonAutoDetect
public record FeatureFlagsDTO()
{
}
