package ru.student.api.controller;

import com.fasterxml.jackson.annotation.JsonAlias;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.student.api.service.TokenSessionService;
import ru.student.api.service.TokenSessionService.Pair;

@RestController
@Profile("!init")
@RequestMapping("/auth")
public class TokenController {
    public record Login(@JsonAlias("username") String login, String password, String deviceId) {}
    public record Registration(@JsonAlias("username") String login, String password, String name, String deviceId) {}
    public record Refresh(String refreshToken) {}
    private final TokenSessionService sessions;
    public TokenController(TokenSessionService sessions) { this.sessions = sessions; }

    @PostMapping("/login")
    public Pair login(@RequestBody Login body) {
        return sessions.login(body.login(), body.password(), body.deviceId());
    }

    @PostMapping("/register")
    public ResponseEntity<Pair> register(@RequestBody Registration body) {
        return ResponseEntity.status(201).body(sessions.register(body.login(), body.password(), body.name(), body.deviceId()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Pair> refresh(@RequestBody Refresh body) {
        return sessions.rotate(body.refreshToken()).map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.status(401).build());
    }
}
