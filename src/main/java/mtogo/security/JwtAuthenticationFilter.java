package mtogo.security;

import java.io.IOException;
import java.util.List;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import mtogo.exceptions.UnauthorizedException;
import mtogo.feature.user.domain.UserService;
import mtogo.feature.user.presentation.dto.response.UserDTO;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter
{
    private final UserService users;
    private final SecurityProblemHandler problems;

    public JwtAuthenticationFilter(UserService users, SecurityProblemHandler problems)
    {
        this.users = users;
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException
    {
        String authorization = request.getHeader("Authorization");
        if (authorization != null)
        {
            if (!authorization.startsWith("Bearer ") || authorization.length() == 7)
            {
                problems.write(request, response, HttpStatus.UNAUTHORIZED, "Invalid access token");
                return;
            }
            try
            {
                UserDTO user = users.authenticate(authorization.substring(7));
                var authentication = UsernamePasswordAuthenticationToken.authenticated(user.id().toString(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.role())));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (UnauthorizedException exception)
            {
                problems.write(request, response, HttpStatus.UNAUTHORIZED, "Invalid or expired access token");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
