package com.jio.eim.user.service;

import com.jio.eim.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expiryMillis;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiry-hours}") int expiryHours) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expiryMillis = expiryHours * 3600L * 1000L;
    }

    public String generate(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiryMillis);

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .claim("email", user.getEmail())
                .claim("role", user.getRole())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    public Claims validateAndParse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getExpiresInSeconds() {
        return expiryMillis / 1000L;
    }
}
