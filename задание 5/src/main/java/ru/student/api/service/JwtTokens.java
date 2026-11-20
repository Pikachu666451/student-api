package ru.student.api.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.exceptions.JWTVerificationException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!init")
public class JwtTokens {
    public record Issued(String value, Instant expiry) {}
    private final Algorithm algorithm;
    private final long accessSeconds;
    private final long refreshSeconds;

    public JwtTokens(@Value("${jwt.secret}") String secret, @Value("${jwt.access-seconds}") long accessSeconds,
                     @Value("${jwt.refresh-seconds}") long refreshSeconds) {
        byte[] key = Base64.getDecoder().decode(secret);
        if (key.length < 32 || accessSeconds <= 0 || refreshSeconds <= 0) {
            throw new IllegalArgumentException("Проверьте ключ JWT и сроки действия токенов");
        }
        this.algorithm = Algorithm.HMAC256(key);
        this.accessSeconds = accessSeconds;
        this.refreshSeconds = refreshSeconds;
    }

    public Issued access(String username, long userId) { return issue(username, userId, "access", accessSeconds); }
    public Issued refresh(String username, long userId) { return issue(username, userId, "refresh", refreshSeconds); }
    public DecodedJWT verifyAccess(String token) { return verify(token, "access"); }
    public DecodedJWT verifyRefresh(String token) { return verify(token, "refresh"); }

    private Issued issue(String username, long userId, String type, long seconds) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expiry = now.plusSeconds(seconds);
        String value = JWT.create().withSubject(username).withClaim("uid", userId).withClaim("type", type)
            .withJWTId(UUID.randomUUID().toString()).withIssuedAt(now).withExpiresAt(expiry).sign(algorithm);
        return new Issued(value, expiry);
    }

    private DecodedJWT verify(String token, String type) {
        DecodedJWT jwt = JWT.require(algorithm).withClaim("type", type).withClaimPresence("iat")
            .withClaimPresence("exp").withClaimPresence("uid").withClaimPresence("sub")
            .withClaimPresence("jti").build().verify(token);
        if (jwt.getSubject() == null || jwt.getSubject().isBlank() || jwt.getId() == null || jwt.getId().isBlank()
                || jwt.getClaim("uid").asLong() == null || jwt.getExpiresAtAsInstant() == null
                || jwt.getIssuedAtAsInstant() == null) {
            throw new JWTVerificationException("Invalid claims");
        }
        return jwt;
    }
}
