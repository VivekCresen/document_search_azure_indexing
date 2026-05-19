package com.cresensolutions.document_search_azure_indexing.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.Key;
import java.util.Collection;
import java.util.List;

@Component
@Slf4j
public class JwtAdminAuthenticationFilter extends OncePerRequestFilter {

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    @Value("${indexing.security.internal-token}")
    private String internalToken;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            authenticateInternalService(request);
            authenticateAdminJwt(request);
        } catch (Exception ex) {
            log.warn("Failed to authenticate indexing request: {}", ex.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private boolean authenticateInternalService(HttpServletRequest request) {
        String token = request.getHeader("X-Internal-Service-Token");
        if (!StringUtils.hasText(token) || !token.equals(internalToken)) {
            return false;
        }
        setAuthentication("document-service", List.of("ROLE_ADMIN"));
        return true;
    }

    private boolean authenticateAdminJwt(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            return false;
        }

        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key())
                .build()
                .parseClaimsJws(authHeader.substring(7))
                .getBody();

        List<String> roles = extractRoles(claims.get("roles"));
        if (!roles.contains("ROLE_ADMIN")) {
            return false;
        }

        setAuthentication(claims.getSubject(), roles);
        return true;
    }

    private List<String> extractRoles(Object roles) {
        if (roles instanceof Collection<?> collection) {
            return collection.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        }
        return List.of();
    }

    private void setAuthentication(String principal, List<String> roles) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                roles.stream().map(SimpleGrantedAuthority::new).toList()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private Key key() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }
}
