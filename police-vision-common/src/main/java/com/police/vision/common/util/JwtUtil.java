package com.police.vision.common.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class JwtUtil {

    private static final String SECRET = "police-vision-command-secret-key-2024-abcdefghijklmn";
    private static final long EXPIRE_TIME = 7200 * 1000;
    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private JwtUtil() {}

    public static String generateToken(Long userId, String policeNo, String name) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("policeNo", policeNo);
        claims.put("name", name);

        Date now = new Date();
        Date expireDate = new Date(now.getTime() + EXPIRE_TIME);

        return Jwts.builder()
                .claims(claims)
                .issuedAt(now)
                .expiration(expireDate)
                .signWith(SECRET_KEY)
                .compact();
    }

    public static Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(SECRET_KEY)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.warn("Token已过期：{}", e.getMessage());
            return null;
        } catch (JwtException e) {
            log.warn("Token无效：{}", e.getMessage());
            return null;
        }
    }

    public static boolean validateToken(String token) {
        Claims claims = parseToken(token);
        return claims != null && claims.getExpiration().after(new Date());
    }

    public static Long getUserId(String token) {
        Claims claims = parseToken(token);
        if (claims != null) {
            Object userId = claims.get("userId");
            return userId != null ? Long.valueOf(userId.toString()) : null;
        }
        return null;
    }

    public static String getPoliceNo(String token) {
        Claims claims = parseToken(token);
        if (claims != null) {
            Object policeNo = claims.get("policeNo");
            return policeNo != null ? policeNo.toString() : null;
        }
        return null;
    }

    public static long getExpireTime() {
        return EXPIRE_TIME / 1000;
    }
}
