package mtogo.testsupport;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Starts the application on a random port with authentication enabled and a
 * test signing secret, for endpoint tests that send tokens. The flag is pinned
 * so a developer's local .env cannot change the result.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"FEATURE_AUTH_ENABLED=true",
        "JWT_SECRET=0123456789abcdef0123456789abcdef0123456789abcdef"})
public @interface AuthenticatedEndpointTest {
}
