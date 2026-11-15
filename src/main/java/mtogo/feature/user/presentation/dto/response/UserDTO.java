package mtogo.feature.user.presentation.dto.response;

public record UserDTO(Long id, String email, String role, UserProfileDTO profile)
{
}
