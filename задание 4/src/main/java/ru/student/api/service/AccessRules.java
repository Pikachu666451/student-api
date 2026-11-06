package ru.student.api.service;

import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import ru.student.api.model.*;
import ru.student.api.repository.RecyclingRepository;

@Component("accessRules")
@Profile("!init")
public class AccessRules {
    private final JdbcTemplate jdbc;
    private final RecyclingRepository repository;
    public AccessRules(JdbcTemplate jdbc, RecyclingRepository repository) {
        this.jdbc = jdbc;
        this.repository = repository;
    }
    public boolean admin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(role -> role.getAuthority().equals("ROLE_ADMIN"));
    }
    public boolean owner(Authentication auth, Long id) {
        return id != null && jdbc.queryForObject("SELECT COUNT(*) FROM accounts WHERE login=? AND userId=? AND enabled=TRUE", Integer.class, auth.getName(), id) > 0;
    }
    public boolean canRead(Authentication auth, String resource, Long id) {
        if (!Set.of("users", "recycling-types", "collection-points", "recycling-batches", "bonus-entries").contains(resource)) return false;
        if (admin(auth) || resource.equals("recycling-types") || resource.equals("collection-points")) return true;
        if (resource.equals("users")) return owner(auth, id);
        Entity entity = repository.findById(resource, id);
        if (entity instanceof RecyclingBatch batch) return owner(auth, batch.userId);
        if (entity instanceof BonusEntry bonus) {
            Entity linked = repository.findById("recycling-batches", bonus.batchId);
            return linked instanceof RecyclingBatch batch && owner(auth, batch.userId);
        }
        return false;
    }
}
