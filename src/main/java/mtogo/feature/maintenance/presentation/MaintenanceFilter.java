package mtogo.feature.maintenance.presentation;

import java.io.IOException;
import java.util.Map;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import mtogo.feature.maintenance.domain.MaintainableFeature;
import mtogo.feature.maintenance.domain.MaintenanceService;
import mtogo.security.SecurityProblemHandler;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

public class MaintenanceFilter extends OncePerRequestFilter
{
    private static final Map<String, MaintainableFeature> MAINTAINABLE_POSTS = Map.of("/v1/orders",
            MaintainableFeature.ORDERING, "/v1/users", MaintainableFeature.AUTHENTICATION, "/v1/users/login",
            MaintainableFeature.AUTHENTICATION);

    private final MaintenanceService maintenance;
    private final SecurityProblemHandler problems;

    public MaintenanceFilter(MaintenanceService maintenance, SecurityProblemHandler problems)
    {
        this.maintenance = maintenance;
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException
    {
        if (HttpMethod.POST.matches(request.getMethod()))
        {
            MaintainableFeature feature = MAINTAINABLE_POSTS.get(request.getServletPath());
            if (feature != null)
            {
                var message = maintenance.getPauseMessage(feature);
                if (message.isPresent())
                {
                    problems.write(request, response, HttpStatus.SERVICE_UNAVAILABLE, message.get());
                    return;
                }
            }
        }
        chain.doFilter(request, response);
    }
}
