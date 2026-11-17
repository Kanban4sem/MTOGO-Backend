package mtogo.featureflag.presentation;

import java.io.IOException;
import java.util.List;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import mtogo.security.SecurityProblemHandler;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

// Runs before authentication and validation, so while a flag is off its
// endpoints don't exist for anyone. Not a @Component: SecurityConfig adds it to
// the security chain, and Spring Boot must not register it a second time.
public class FeatureGateFilter extends OncePerRequestFilter
{
    private final List<FeatureGate> gates;
    private final SecurityProblemHandler problems;

    public FeatureGateFilter(List<FeatureGate> gates, SecurityProblemHandler problems)
    {
        this.gates = gates;
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException
    {
        String path = request.getServletPath();
        var closedGate = gates.stream().filter(gate -> gate.isClosedFor(path)).findFirst();
        if (closedGate.isPresent())
        {
            problems.write(request, response, HttpStatus.NOT_FOUND, closedGate.get().detail());
            return;
        }
        chain.doFilter(request, response);
    }
}
