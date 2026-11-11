package mtogo.server;

import mtogo.config.AppProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class Server implements WebMvcConfigurer
{

    // Attributes
    private final AppProperties appProperties;

    // _________________________________________________________________________________________________________________

    public Server(AppProperties appProperties)
    {
        this.appProperties = appProperties;
    }

    // _________________________________________________________________________________________________________________

    @Override
    public void addCorsMappings(CorsRegistry corsRegistry)
    {
        corsRegistry.addMapping("/v1/**").allowedOrigins(appProperties.cors().allowedOrigin())
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type");
    }

}