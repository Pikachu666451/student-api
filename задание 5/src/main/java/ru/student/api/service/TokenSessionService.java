package ru.student.api.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import ru.student.api.model.*;
import ru.student.api.repository.SessionRepository;

@Service
@Profile("!init")
@Transactional
public class TokenSessionService {
    public record Pair(String accessToken, String refreshToken) {}
    private final AccountService accounts;
    private final PasswordEncoder encoder;
    private final SessionRepository sessions;
    private final JwtTokens jwt;

    public TokenSessionService(AccountService accounts, PasswordEncoder encoder, SessionRepository sessions, JwtTokens jwt) {
        this.accounts = accounts;
        this.encoder = encoder;
        this.sessions = sessions;
        this.jwt = jwt;
    }

    public Pair register(String login, String password, String name, String device) {
        var account = accounts.register(login, password, name);
        return issue((String) account.get("login"), (Long) account.get("userId"), device);
    }

    public Pair login(String login, String password, String device) {
        if (login == null || login.isBlank() || password == null || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        try {
            var user = accounts.loadUserByUsername(login);
            if (!user.isEnabled() || !encoder.matches(password, user.getPassword())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
            }
            return issue(user.getUsername(), (Long) accounts.me(user.getUsername()).get("userId"), device);
        } catch (UsernameNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }

    public Optional<Pair> rotate(String token) {
        if (token == null || token.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        try {
            var decoded = jwt.verifyRefresh(token);
            var row = sessions.lock(token).orElse(null);
            if (row == null || !row.username().equals(decoded.getSubject())
                    || !row.refreshTokenExpiry().isAfter(Instant.now())) return Optional.empty();
            var user = accounts.loadUserByUsername(row.username());
            long uid = decoded.getClaim("uid").asLong();
            if (!user.isEnabled() || !accounts.me(row.username()).get("userId").equals(uid)) return Optional.empty();
            if (row.status() == SessionStatus.USED) {
                sessions.setStatus(row.id(), SessionStatus.REVOKED);
                return Optional.empty();
            }
            if (row.status() != SessionStatus.ACTIVE) return Optional.empty();
            sessions.setStatus(row.id(), SessionStatus.USED);
            return Optional.of(issue(row.username(), uid, row.deviceId()));
        } catch (JWTVerificationException | UsernameNotFoundException e) {
            return Optional.empty();
        }
    }

    private Pair issue(String username, long uid, String device) {
        if (device != null && device.length() > 200) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        var access = jwt.access(username, uid);
        var refresh = jwt.refresh(username, uid);
        sessions.save(new UserSession(UUID.randomUUID(), username, device, access.value(), refresh.value(),
            access.expiry(), refresh.expiry(), SessionStatus.ACTIVE));
        return new Pair(access.value(), refresh.value());
    }
}
