package com.ciao.backend.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import java.security.Key;
import java.util.Date;

@Component
public class JwtUtils {

    @Value("${ciao.app.jwtSecret:mySuperSecretKeyForCiaoApplicationWhichNeedsToBeAtLeast32Bytes}")
    private String jwtSecret;

    @Value("${ciao.app.jwtExpirationMs:86400000}")
    private int jwtExpirationMs;

    @Value("${ciao.app.jwtCookieName:ciao_jwt}")
    private String jwtCookie;
    
    // Configurable secure flag (false for local dev, true for prod)
    @Value("${ciao.app.cookieSecure:false}")
    private boolean cookieSecure;

    public String getJwtFromCookies(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, jwtCookie);
        if (cookie != null) {
            return cookie.getValue();
        } else {
            return null;
        }
    }

    public ResponseCookie generateJwtCookie(UserDetailsImpl userPrincipal) {
        String jwt = generateTokenFromUsername(userPrincipal.getUsername());
        return ResponseCookie.from(jwtCookie, jwt)
                .path("/api")
                .maxAge(jwtExpirationMs / 1000)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSecure ? "None" : "Lax")
                .build();
    }

    public ResponseCookie getCleanJwtCookie() {
        return ResponseCookie.from(jwtCookie, "")
                .path("/api")
                .maxAge(0)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSecure ? "None" : "Lax")
                .build();
    }

    public String getUserNameFromJwtToken(String token) {
        return Jwts.parserBuilder().setSigningKey(key()).build()
                .parseClaimsJws(token).getBody().getSubject();
    }

    private Key key() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    public boolean validateJwtToken(String authToken) {
        try {
            Claims claims = Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(authToken).getBody();
            return "AUTH".equals(claims.get("type")) && claims.getSubject() != null;
        } catch (MalformedJwtException e) {
            System.err.println("Invalid JWT token: " + e.getMessage());
        } catch (ExpiredJwtException e) {
            System.err.println("JWT token is expired: " + e.getMessage());
        } catch (UnsupportedJwtException e) {
            System.err.println("JWT token is unsupported: " + e.getMessage());
        } catch (JwtException e) {
            return false;
        } catch (IllegalArgumentException e) {
            System.err.println("JWT claims string is empty: " + e.getMessage());
        }
        return false;
    }

    public String generateTokenFromUsername(String username) {
        return Jwts.builder()
                .setSubject(username)
                .claim("type", "AUTH")
                .setIssuedAt(new Date())
                .setExpiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
    }

    // --- Phase 3: Guest Reservation JWT Methods ---
    
    public String generateGuestReservationToken(Integer reservationId) {
        // Guest tokens live for a short time (e.g. 2 hours)
        return Jwts.builder()
                .setSubject(String.valueOf(reservationId))
                .claim("type", "GUEST_RESERVATION")
                .setIssuedAt(new Date())
                .setExpiration(new Date((new Date()).getTime() + 7200000)) 
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
    }

    public Integer getReservationIdFromGuestToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(token).getBody();
            if ("GUEST_RESERVATION".equals(claims.get("type"))) {
                return Integer.parseInt(claims.getSubject());
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }
}

