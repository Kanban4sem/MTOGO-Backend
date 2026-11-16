package mtogo.security;

import static org.junit.jupiter.api.Assertions.*;

import mtogo.exceptions.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class PasswordServiceTest
{
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final PasswordService passwords = new PasswordService(encoder);

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvFileSource(resources = "/testcases/security/password-service/hash-utf8-bytes.csv", numLinesToSkip = 1)
    @DisplayName("Hash - the BCrypt limit counts UTF-8 bytes rather than Java characters")
    void hashUtf8Bytes(int bytes, String expected, String reason)
    {
        String password = "\u00e9".repeat(bytes / 2) + (bytes % 2 == 0 ? "" : "a");
        if (expected.equals("ACCEPT"))
        {
            String hash = passwords.hash(password);
            assertNotEquals(password, hash);
            assertTrue(encoder.matches(password, hash));
            assertTrue(passwords.matches(password, hash));
        } else
        {
            assertThrows(BadRequestException.class, () -> passwords.hash(password));
            String hash = passwords.hash("Customer123!");
            assertFalse(passwords.matches(password, hash));
        }
    }
}
