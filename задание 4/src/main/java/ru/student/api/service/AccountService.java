package ru.student.api.service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.student.api.repository.RecyclingRepository;

@Service
@Profile("!init")
public class AccountService implements UserDetailsService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final RecyclingRepository repository;

    public AccountService(JdbcTemplate jdbc, PasswordEncoder encoder, RecyclingRepository repository) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.repository = repository;
    }

    public static boolean strongPassword(String password) {
        return password != null && password.length() >= 10
            && password.getBytes(StandardCharsets.UTF_8).length <= 72
            && password.matches("(?s).*[a-z].*") && password.matches("(?s).*[A-Z].*")
            && password.matches("(?s).*[0-9].*") && password.matches("(?s).*[^a-zA-Z0-9\\s].*");
    }

    public UserDetails loadUserByUsername(String login) {
        var users = jdbc.query("SELECT login,password,role,enabled FROM accounts WHERE login=?", (row, number) ->
            User.withUsername(row.getString("login")).password(row.getString("password"))
                .roles(row.getString("role")).disabled(!row.getBoolean("enabled")).build(), login.toLowerCase(Locale.ROOT));
        if (users.isEmpty()) throw new UsernameNotFoundException("Учётная запись не найдена");
        return users.getFirst();
    }

    @Transactional
    public Map<String, Object> register(String login, String password, String name) {
        if (login == null || !login.matches("[a-zA-Z0-9_]{3,40}") || name == null || name.isBlank() || name.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Проверьте имя и логин: 3–40 латинских букв, цифр или _");
        }
        if (!strongPassword(password)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Пароль: минимум 10 символов, строчная и заглавная латинская буква, цифра, спецсимвол; максимум 72 байта UTF-8");
        }
        login = login.toLowerCase(Locale.ROOT);
        repository.lock();
        if (jdbc.queryForObject("SELECT COUNT(*) FROM accounts WHERE login=?", Integer.class, login) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Логин уже занят");
        }
        var user = new ru.student.api.model.User();
        user.name = name.trim();
        repository.save("users", user);
        jdbc.update("INSERT INTO accounts(userId,login,password,role,enabled) VALUES(?,?,?,'USER',TRUE)", user.id, login, encoder.encode(password));
        return Map.of("userId", user.id, "login", login, "role", "USER");
    }

    public Map<String, Object> me(String login) {
        return jdbc.queryForObject("SELECT userId,login,role,enabled FROM accounts WHERE login=?", (row, number) ->
            Map.of("userId", row.getLong("userId"), "login", row.getString("login"), "role", row.getString("role"), "enabled", row.getBoolean("enabled")), login);
    }

    @Transactional
    public void setEnabled(Long userId, Boolean enabled, String login) {
        if (enabled == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите enabled");
        repository.lock();
        if (me(login).get("userId").equals(userId)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Нельзя заблокировать себя");
        if (jdbc.update("UPDATE accounts SET enabled=? WHERE userId=? AND role='USER'", enabled, userId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Обычная учётная запись не найдена");
        }
    }
}
