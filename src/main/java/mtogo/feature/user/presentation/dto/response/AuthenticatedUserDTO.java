package mtogo.feature.user.presentation.dto.response;

import java.time.Instant;

public record AuthenticatedUserDTO(String accessToken, String tokenType, Instant expiresAt, UserDTO user)
{
}
