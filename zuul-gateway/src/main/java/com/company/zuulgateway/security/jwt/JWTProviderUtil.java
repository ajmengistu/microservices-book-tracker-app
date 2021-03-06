package com.company.zuulgateway.security.jwt;

import java.security.Key;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.stream.Collectors;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

/**
 * A class to create and validate a Json Web Token (JWT) for a User.
 */
@Component
@Slf4j
public class JWTProviderUtil implements InitializingBean {

    private static final String AUTHORITIES_KEY = "auth";

    private String secret = "21bVPgn9p1pSfjUi5B57vuleRikZ0I3Q58ThEyhO6GAG3qTj4cJyNKuk/GFlpmu0QalAC1SVlOme2jHbHQ1x3w==";

    private Long expiration = 24L * 60 * 60 * 1000; // 24 hours

    private Key key;

    public JWTProviderUtil() {
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        byte[] keyBytes;
        log.debug("Using a Base64-encoded JWT secret key");
        keyBytes = Decoders.BASE64.decode(secret);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public String createToken(Authentication authentication) {
        // @formatter:off
        String authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));
        return Jwts.builder()
            .setSubject(authentication.getName())
            .claim(AUTHORITIES_KEY, authorities)
            .signWith(key, SignatureAlgorithm.HS512)
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + expiration))
            .compact();
    }

    public Authentication getAuthentication(String token) {
        // @formatter:off
        Claims claims = Jwts.parserBuilder()
            .setSigningKey(key)
            .build()
            .parseClaimsJws(token)
            .getBody();
        Collection<? extends GrantedAuthority> authorities = 
            Arrays.stream(claims.get(AUTHORITIES_KEY).toString().split(","))
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
        User principal = new User(claims.getSubject(), "", authorities);
        // MUST use the 3 paramter UsernamePassworAuthenticationToken constructor, to mark
        // the current user as Authenticated. (see the UsernamePasswordAuthenticationToken constructor).
        return new UsernamePasswordAuthenticationToken(principal, token, authorities);
        // return new UsernamePasswordAuthenticationToken(claims.getSubject(), authorities); // DO NOT DO THIS
        // WHY? Using this 2 parameter constructor, does not make the current user authenticated, thus
        // Spring Security will see this user as unauthenticated even though they are via the valid JWT.
    }

    public boolean validateToken(String authToken){
        // @formatter:on
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(authToken);
            return true;
        } catch (io.jsonwebtoken.security.SecurityException | MalformedJwtException e) {
            log.info("Invalid JWT signature.");
            log.trace("Invalid JWT signature trace: {}", e);
        } catch (ExpiredJwtException e) {
            log.info("Expired JWT token.");
            log.trace("Expired JWT token trace: {}", e);
        } catch (UnsupportedJwtException e) {
            log.info("Unsupported JWT token.");
            log.trace("Unsupported JWT token trace: {}", e);
        } catch (IllegalArgumentException e) {
            log.info("JWT token compact of handler are invalid.");
            log.trace("JWT token compact of handler are invalid trace: {}", e);
        }
        return false;
    }
}