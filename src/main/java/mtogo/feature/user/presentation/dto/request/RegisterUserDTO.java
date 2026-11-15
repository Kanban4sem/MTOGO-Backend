package mtogo.feature.user.presentation.dto.request;

import jakarta.validation.constraints.*;

public record RegisterUserDTO(@NotBlank @Size(max = 100) String firstName, @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(min = 8, max = 72) String confirmPassword, @Size(max = 30) String phoneNumber,
        @Pattern(regexp = "da|en") String preferredLanguage, @Size(max = 20) String postalCode,
        @Size(max = 100) String city, @Size(max = 100) String country)
{
}
