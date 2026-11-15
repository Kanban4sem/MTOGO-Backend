package mtogo.feature.user.presentation.dto.request;

import jakarta.validation.constraints.*;

public record LoginUserDTO(@NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 72) String password)
{
}
