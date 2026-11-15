package mtogo.security;

import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import mtogo.exceptions.UnauthorizedException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import mtogo.config.AppProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService
{
    private final SecretKey key;
    private final long lifetimeSeconds;

    public JwtService(AppProperties properties, @Value("${app.jwt.secret:}") String secret,
            @Value("${app.jwt.lifetime-seconds:900}") long lifetimeSeconds)
    {
        if (lifetimeSeconds < 1 || lifetimeSeconds > 86400)
        {
            throw new IllegalArgumentException("JWT lifetime must be between 1 and 86400 seconds");
        }
        if (properties.isAuthEnabled() && secret.isBlank())
        {
            throw new IllegalArgumentException(
                    "JWT_SECRET must contain a Base64 signing key when authentication is enabled");
        }
        this.key = secret.isBlank() ? Jwts.SIG.HS256.key().build() : Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.lifetimeSeconds = lifetimeSeconds;
    }

    public IssuedToken issue(Long userId, long sessionVersion)
    {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(lifetimeSeconds);
        String token = Jwts.builder().issuer("mtogo").subject(userId.toString()).issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt)).claim("sessionVersion", sessionVersion)
                .id(java.util.UUID.randomUUID().toString()).signWith(key).compact();
        return new IssuedToken(token, expiresAt);
    }

    public VerifiedToken verify(String token)
    {
        try
        {
            Claims claims = Jwts.parser().verifyWith(key).requireIssuer("mtogo").build().parseSignedClaims(token)
                    .getPayload();
            Number sessionVersion = claims.get("sessionVersion", Number.class);
            if (claims.getExpiration() == null || sessionVersion == null)
            {
                throw new IllegalArgumentException("Missing token claims");
            }
            return new VerifiedToken(Long.valueOf(claims.getSubject()), sessionVersion.longValue());
        } catch (JwtException | IllegalArgumentException exception)
        {
            throw new UnauthorizedException("Invalid or expired access token", exception);
        }
    }

    public record IssuedToken(String value, Instant expiresAt)
    {
    }
    public record VerifiedToken(Long userId, long sessionVersion)
    {
    }
}
