package mtogo.server;

import mtogo.config.AppProperties;
import mtogo.feature.user.domain.UserSeedService;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

// Seeds after every bean exists, so after Flyway has migrated and JPA has started,
// and before the web server accepts requests. A Flyway callback cannot seed through
// the repositories, because Flyway runs before JPA starts. A failure stops startup.
@Component
public class UserSeedInitializer implements SmartInitializingSingleton
{
    private final UserSeedService seedService;
    private final AppProperties appProperties;

    public UserSeedInitializer(UserSeedService seedService, AppProperties appProperties)
    {
        this.seedService = seedService;
        this.appProperties = appProperties;
    }

    @Override
    public void afterSingletonsInstantiated()
    {
        seedService.seed(appProperties.seed());
    }
}
