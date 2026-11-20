package ru.student.api.repository;

import java.time.OffsetDateTime;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.student.api.model.*;

@Repository
public class SessionRepository {
    private final JdbcTemplate jdbc;

    public SessionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(UserSession s) {
        jdbc.update("""
            INSERT INTO user_sessions(id,username,device_id,access_token,refresh_token,
                access_token_expiry,refresh_token_expiry,status) VALUES(?,?,?,?,?,?,?,?)
            """, s.id(), s.username(), s.deviceId(), s.accessToken(), s.refreshToken(),
            Timestamp.from(s.accessTokenExpiry()), Timestamp.from(s.refreshTokenExpiry()), s.status().name());
    }

    public Optional<UserSession> lock(String refresh) {
        return jdbc.query("SELECT * FROM user_sessions WHERE refresh_token=? FOR UPDATE", (r, n) ->
            new UserSession(r.getObject("id", UUID.class), r.getString("username"), r.getString("device_id"),
                r.getString("access_token"), r.getString("refresh_token"),
                r.getObject("access_token_expiry", OffsetDateTime.class).toInstant(),
                r.getObject("refresh_token_expiry", OffsetDateTime.class).toInstant(),
                SessionStatus.valueOf(r.getString("status"))), refresh).stream().findFirst();
    }

    public void setStatus(UUID id, SessionStatus status) {
        jdbc.update("UPDATE user_sessions SET status=? WHERE id=?", status.name(), id);
    }
}
