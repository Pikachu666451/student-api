package ru.student.api.controller;

import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import ru.student.api.service.AccountService;

@RestController
@Profile("!init")
@RequestMapping("/api")
public class AuthController {
    private final AccountService accounts;
    public AuthController(AccountService accounts) { this.accounts = accounts; }
    public record Registration(String login, String password, String name) {}
    public record AccountState(Boolean enabled) {}

    @GetMapping("/auth/csrf")
    public CsrfToken csrf(CsrfToken token) { return token; }

    @PostMapping("/auth/register")
    public ResponseEntity<?> register(@RequestBody Registration input) {
        return ResponseEntity.status(201).body(accounts.register(input.login(), input.password(), input.name()));
    }

    @GetMapping("/auth/me")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> me(Authentication auth) { return accounts.me(auth.getName()); }

    @PatchMapping("/admin/users/{id}/state")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> state(@PathVariable Long id, @RequestBody AccountState input, Authentication auth) {
        accounts.setEnabled(id, input.enabled(), auth.getName());
        return ResponseEntity.noContent().build();
    }
}
