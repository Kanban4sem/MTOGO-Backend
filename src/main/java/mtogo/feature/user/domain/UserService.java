package mtogo.feature.user.domain;

import mtogo.feature.role.domain.RoleName;
import mtogo.feature.role.data.RoleRepository;
import mtogo.exceptions.*;
import mtogo.feature.user.data.*;
import mtogo.feature.user.presentation.dto.request.LoginUserDTO;
import mtogo.feature.user.presentation.dto.request.RegisterUserDTO;
import mtogo.feature.user.presentation.dto.request.UpdateCustomerProfileDTO;
import mtogo.feature.user.presentation.dto.response.UserDTO;
import mtogo.feature.user.presentation.dto.response.AuthenticatedUserDTO;
import mtogo.security.JwtService;
import mtogo.security.PasswordService;
import mtogo.feature.address.domain.AddressValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService
{
    private final UserRepository users;
    private final RoleRepository roles;
    private final UserMapper mapper;
    private final PasswordService passwords;
    private final AddressValidator addressValidator;
    private final JwtService tokens;

    public UserService(UserRepository users, RoleRepository roles, UserMapper mapper, PasswordService passwords,
            AddressValidator addressValidator, JwtService tokens)
    {
        this.users = users;
        this.roles = roles;
        this.mapper = mapper;
        this.passwords = passwords;
        this.addressValidator = addressValidator;
        this.tokens = tokens;
    }

    @Transactional
    public UserDTO register(RegisterUserDTO request)
    {
        if (!request.password().equals(request.confirmPassword()))
        {
            throw new BadRequestException("Passwords must match");
        }
        addressValidator.validate(request.postalCode(), request.city(), request.country());
        String email = EmailUtil.normalize(request.email());
        if (users.existsByEmail(email))
        {
            throw new ConflictException("An account with this email already exists");
        }
        User user = mapper.toEntity(request);
        user.activate(passwords.hash(request.password()), roles.findByName(RoleName.CUSTOMER).orElseThrow(), null);
        return mapper.toDTO(users.saveAndFlush(user));
    }

    @Transactional(readOnly = true)
    public AuthenticatedUserDTO login(LoginUserDTO request)
    {
        User user = users.findByEmail(EmailUtil.normalize(request.email())).orElse(null);
        boolean matches = passwords.matches(request.password(), user == null ? null : user.getPasswordHash());
        if (user == null || !matches || !user.isActive())
        {
            throw new UnauthorizedException("Invalid email or password");
        }
        JwtService.IssuedToken token = tokens.issue(user.getId(), user.getSessionVersion());
        return new AuthenticatedUserDTO(token.value(), "Bearer", token.expiresAt(), mapper.toDTO(user));
    }

    @Transactional(readOnly = true)
    public UserDTO authenticate(String token)
    {
        JwtService.VerifiedToken verified = tokens.verify(token);
        User user = requireActiveUser(verified.userId());
        if (user.getSessionVersion() != verified.sessionVersion())
        {
            throw new UnauthorizedException("Invalid access token");
        }
        return mapper.toDTO(user);
    }

    @Transactional(readOnly = true)
    public UserDTO getCurrent(Long userId)
    {
        return mapper.toDTO(requireActiveUser(userId));
    }

    @Transactional
    public UserDTO updateCustomerProfile(Long userId, UpdateCustomerProfileDTO request)
    {
        User user = requireActiveUser(users.findByIdForUpdate(userId).orElse(null));
        mapper.updateProfile(user.getProfile(), request);
        return mapper.toDTO(user);
    }

    @Transactional
    public void logout(Long userId)
    {
        users.revokeSessions(userId);
    }

    private User requireActiveUser(Long userId)
    {
        return requireActiveUser(users.findById(userId).orElse(null));
    }

    private static User requireActiveUser(User user)
    {
        if (user == null || !user.isActive())
        {
            throw new UnauthorizedException("Invalid access token");
        }
        return user;
    }

}
