package com.codelens.ai.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

/**
 * Handles JWT creation and validation.
 *
 * JWT structure: header.payload.signature
 * - header: algorithm used (HS256)
 * - payload: claims (username, issued-at, expiry)
 * - signature: HMAC of header+payload using our secret key
 *
 * Only we can create valid tokens (we hold the secret).
 * Anyone can read the payload (it's Base64, not encrypted).
 * Nobody can forge a token without our secret.
 */
@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs) {
        // Decode the Base64 secret from properties into a SecretKey
        this.secretKey = Keys.hmacShaKeyFor(
                Base64.getDecoder().decode(secret));
        this.expirationMs = expirationMs;
    }

    /**
     * Generate a JWT for the given username.
     * The token contains:
     * - subject: username (used to identify the user on each request)
     * - issuedAt: now
     * - expiration: now + expirationMs (default 24 hours)
     */
    public String generateToken(String username) {
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(secretKey)
                .compact();
    }

    /**
     * Extract the username from a token.
     * Throws if the token is invalid or expired.
     */
    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }

    /**
     * Returns true if:
     * 1. The token's username matches the expected username
     * 2. The token hasn't expired
     */
    public boolean isTokenValid(String token, String username) {
        try {
            String extracted = extractUsername(token);
            return extracted.equals(username) && !isExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isExpired(String token) {
        return extractClaims(token)
                .getExpiration()
                .before(new Date());
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}