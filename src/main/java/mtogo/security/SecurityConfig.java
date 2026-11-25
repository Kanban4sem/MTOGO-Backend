package mtogo.security;

import java.util.List;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import mtogo.feature.user.domain.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class SecurityConfig
{
    @Bean
    public PasswordEncoder passwordEncoder()
    {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, UserService users,
            SecurityProblemHandler problems, List<FeatureGate> gates) throws Exception
    {
        // Credentials are accepted only as explicit Bearer headers; no cookie or
        // session authentication is enabled.
        return http.csrf(csrf -> csrf.disable()).cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable()).formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable()).logout(logout -> logout.disable())
                .authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/features").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/restaurants", "/v1/restaurants/*/menus").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                        .permitAll().requestMatchers(HttpMethod.POST, "/v1/users", "/v1/users/login").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/v1/users/me").hasRole("CUSTOMER")
                        .requestMatchers("/v1/users/me/delivery-addresses/**").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.POST, "/v1/orders").hasRole("CUSTOMER")
                        .requestMatchers("/v1/customer/**").hasRole("CUSTOMER").requestMatchers("/v1/restaurant/**")
                        .hasRole("RESTAURANT").requestMatchers("/v1/delivery-agent/**").hasRole("DELIVERY_AGENT")
                        .requestMatchers("/v1/admin/**").hasRole("ADMIN").anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> problems.write(request, response,
                                HttpStatus.UNAUTHORIZED, "Authentication is required"))
                        .accessDeniedHandler((request, response, exception) -> problems.write(request, response,
                                HttpStatus.FORBIDDEN, "Access is denied")))
                .addFilterBefore(new JwtAuthenticationFilter(users, problems),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new FeatureGateFilter(gates, problems), JwtAuthenticationFilter.class).build();
    }
}
