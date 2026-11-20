package ru.student.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.NullSecurityContextRepository;

@Configuration
@Profile("!init")
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ru.student.api.service.JwtTokens jwt,
                                            ru.student.api.service.AccountService accounts) throws Exception {
        http.csrf(csrf -> csrf.ignoringRequestMatchers("/auth/login", "/auth/register", "/auth/refresh")
            .ignoringRequestMatchers(request -> {
                String header = request.getHeader("Authorization");
                return header != null && header.regionMatches(true, 0, "Bearer ", 0, 7);
            }));
        http.addFilterBefore(new BearerFilter(jwt, accounts), org.springframework.security.web.authentication.www.BasicAuthenticationFilter.class);
        var entryPoint = (org.springframework.security.web.AuthenticationEntryPoint) (request, response, error) -> {
            response.setHeader("WWW-Authenticate", "Basic realm=\"recycling\"");
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"Требуется верный логин и пароль активной учётной записи\"}");
        };
        http.authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.POST, "/auth/login", "/auth/register", "/auth/refresh").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
            .requestMatchers("/api/admin/**").hasRole("ADMIN")
            .requestMatchers("/api/**").authenticated()
            .anyRequest().denyAll());
        http.httpBasic(basic -> basic.authenticationEntryPoint(entryPoint)
            .securityContextRepository(new NullSecurityContextRepository()));
        http.securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()));
        http.requestCache(cache -> cache.disable());
        http.formLogin(form -> form.disable());
        http.logout(logout -> logout.disable());
        http.exceptionHandling(errors -> errors.authenticationEntryPoint(entryPoint)
            .accessDeniedHandler((request, response, error) -> {
                response.setStatus(403);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"message\":\"Нет доступа или неверный CSRF-токен\"}");
            }));
        return http.build();
    }
}
