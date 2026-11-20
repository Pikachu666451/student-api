package ru.student.api.config;

import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import ru.student.api.service.*;

public class BearerFilter extends OncePerRequestFilter {
    private final JwtTokens jwt;
    private final AccountService accounts;

    public BearerFilter(JwtTokens jwt, AccountService accounts) {
        this.jwt = jwt;
        this.accounts = accounts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            try {
                var token = jwt.verifyAccess(header.substring(7));
                var user = accounts.loadUserByUsername(token.getSubject());
                if (!user.isEnabled() || !accounts.me(user.getUsername()).get("userId").equals(token.getClaim("uid").asLong())) {
                    response.setStatus(401);
                    return;
                }
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
                SecurityContextHolder.setContext(context);
            } catch (JWTVerificationException | UsernameNotFoundException e) {
                response.setStatus(401);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
