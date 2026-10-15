/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.security;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class JwtUtil {
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    /**
     * Loud start-up warning when the signing secret is the public default from
     * application.yml: anyone who has seen this code could mint admin tokens.
     * (Not fatal, so an existing deployment keeps running — but fix it.)
     */
    @jakarta.annotation.PostConstruct
    void warnOnDefaultSecret() {
        if (secret == null || secret.length() < 64
                || secret.startsWith("navgrow-super-secret-key")) {
            log.error("SECURITY WARNING: jwt.secret is the built-in default or too short. "
                + "Set a long random JWT_SECRET environment variable (64+ characters) and restart.");
        }
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generateToken(UserDetails userDetails, Map<String, Object> claims) {
        Map<String, Object> all = new HashMap<>();
        if (claims != null) all.putAll(claims);
        all.putIfAbsent(TYPE_CLAIM, TYPE_ACCESS);
        return Jwts.builder()
                .claims(all)
                .subject(userDetails.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    /** Token-type claim: access tokens authenticate API calls, refresh tokens only mint new access tokens. */
    public static final String TYPE_CLAIM = "token_type";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(TYPE_CLAIM, TYPE_ACCESS);
        return generateToken(userDetails, claims);
    }

    public String generateRefreshToken(UserDetails userDetails) {
        return Jwts.builder()
                .claim(TYPE_CLAIM, TYPE_REFRESH)
                .subject(userDetails.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    public String extractEmail(String token) {
        return getClaims(token).getSubject();
    }

    /**
     * The token's type ("access" / "refresh"), or null for tokens issued before
     * types existed (still honoured until they expire naturally).
     */
    public String extractType(String token) {
        Object t = getClaims(token).get(TYPE_CLAIM);
        return t == null ? null : t.toString();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            final String email = extractEmail(token);
            return email.equals(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (JwtException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        return getClaims(token).getExpiration().before(new Date());
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}