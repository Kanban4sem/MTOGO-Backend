package mtogo.security;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import mtogo.exceptions.BadRequestException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordService
{
    private static final int MAX_BYTES = 72;
    private final PasswordEncoder encoder;
    private final String dummyHash;

    public PasswordService(PasswordEncoder encoder)
    {
        this.encoder = encoder;
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    public String hash(String password)
    {
        if (password == null || password.isBlank() || password.length() < 8 || exceedsByteLimit(password))
        {
            throw new BadRequestException("Password must contain at least 8 characters and at most 72 UTF-8 bytes");
        }
        return encoder.encode(password);
    }

    public boolean matches(String password, String hash)
    {
        // Unknown accounts still perform a hash comparison to avoid a fast
        // account-existence check.
        return password != null && !exceedsByteLimit(password)
                && encoder.matches(password, hash == null ? dummyHash : hash);
    }

    private static boolean exceedsByteLimit(String password)
    {
        return password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES;
    }
}
