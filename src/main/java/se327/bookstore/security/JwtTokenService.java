package se327.bookstore.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import se327.bookstore.entity.User;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Service
public class JwtTokenService {

    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_USERNAME = "username";

    private final String secret;
    private final long expirationMs;

    public JwtTokenService(
            @Value("${app.jwt.secret:inwza-bookstore-se327-secure-jwt-signing-key-with-256-bits-minimum!}") String secret,
            @Value("${app.jwt.expiration-ms:86400000}") long expirationMs) {
        this.secret = secret;
        this.expirationMs = expirationMs;
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(expirationMs);
        return Jwts.builder()
                .subject(user.getEmail())
                .claim(CLAIM_USER_ID, user.getId())
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim(CLAIM_USERNAME, user.getUsername())
                .claim("iat", now.getEpochSecond())
                .claim("exp", expiry.getEpochSecond())
                .signWith(getSigningKey())
                .compact();
    }

    public String generateToken(BookstoreUserDetails userDetails) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(expirationMs);
        return Jwts.builder()
                .subject(userDetails.getEmail())
                .claim(CLAIM_USER_ID, userDetails.getId())
                .claim(CLAIM_ROLE, userDetails.getRole().name())
                .claim(CLAIM_USERNAME, userDetails.getDisplayName())
                .claim("iat", now.getEpochSecond())
                .claim("exp", expiry.getEpochSecond())
                .signWith(getSigningKey())
                .compact();
    }

    public String extractEmail(String token) {
        return getClaims(token).getSubject();
    }

    public Long extractUserId(String token) {
        Object id = getClaims(token).get(CLAIM_USER_ID);
        if (id instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    public String extractRole(String token) {
        Object role = getClaims(token).get(CLAIM_ROLE);
        return role != null ? role.toString() : null;
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = getClaims(token);
            Object expiration = claims.get("exp");
            if (expiration instanceof Number number) {
                return Instant.ofEpochSecond(number.longValue()).isAfter(Instant.now());
            }
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
