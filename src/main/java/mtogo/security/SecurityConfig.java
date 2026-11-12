package mtogo.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration
public class SecurityConfig
{
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception
    {
        return http.authorizeHttpRequests(auth -> auth
                // Docker's health check calls this without credentials. It is not reachable
                // from outside, because the proxy only forwards /v1/*.
                .requestMatchers("/actuator/health").permitAll()
                // The frontend needs the flags before anyone can log in, since login itself is
                // behind a flag.
                .requestMatchers(HttpMethod.GET, "/v1/features").permitAll()
                // The API documentation only describes the endpoints. Each endpoint keeps its
                // own access rule, and the proxy only forwards /v1/*.
                .requestMatchers(HttpMethod.GET, "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .build();
    }
}
