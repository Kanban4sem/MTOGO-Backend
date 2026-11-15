package mtogo.feature.user.presentation;

import java.net.URI;
import java.security.Principal;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import mtogo.feature.user.presentation.dto.response.AuthenticatedUserDTO;
import mtogo.feature.user.domain.UserService;
import mtogo.feature.user.presentation.dto.request.LoginUserDTO;
import mtogo.feature.user.presentation.dto.request.RegisterUserDTO;
import mtogo.feature.user.presentation.dto.response.UserDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/users")
@Tag(name = "Users", description = "Account registration, authentication and the current user")
public class UserController
{
    private final UserService users;

    public UserController(UserService users)
    {
        this.users = users;
    }

    @PostMapping
    @PreAuthorize("permitAll()")
    @Operation(summary = "Register a customer account")
    @ApiResponse(responseCode = "201", description = "Customer created", content = @Content(schema = @Schema(implementation = UserDTO.class)))
    @ApiResponse(responseCode = "400", description = "Invalid registration fields or incomplete address")
    @ApiResponse(responseCode = "409", description = "Email already exists")
    @ApiResponse(responseCode = "404", description = "Authentication is disabled")
    public ResponseEntity<UserDTO> register(@Valid @RequestBody RegisterUserDTO request)
    {
        UserDTO user = users.register(request);
        return ResponseEntity.created(URI.create("/v1/users/me")).body(user);
    }

    @PostMapping("/login")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Log in and receive a Bearer access token")
    @ApiResponse(responseCode = "200", description = "Authenticated", content = @Content(schema = @Schema(implementation = AuthenticatedUserDTO.class)))
    @ApiResponse(responseCode = "400", description = "Invalid login fields")
    @ApiResponse(responseCode = "401", description = "Invalid email or password")
    @ApiResponse(responseCode = "404", description = "Authentication is disabled")
    public AuthenticatedUserDTO login(@Valid @RequestBody LoginUserDTO request)
    {
        return users.login(request);
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get the current user and profile")
    public UserDTO getCurrent(Principal principal)
    {
        return users.getCurrent(Long.valueOf(principal.getName()));
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Log out on every device by revoking all existing access tokens")
    public ResponseEntity<Void> logout(Principal principal)
    {
        users.logout(Long.valueOf(principal.getName()));
        return ResponseEntity.noContent().build();
    }
}
