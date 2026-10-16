package ru.student.api.repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;
import ru.student.api.model.Entity;

@Repository
public class RecyclingRepository {
    private final Map<String, Map<Long, Entity>> storage = new LinkedHashMap<>();
    private final Map<String, Long> counters = new LinkedHashMap<>();

    public List<Entity> findAll(String resource) {
        return new ArrayList<>(table(resource).values());
    }

    public Entity findById(String resource, Long id) {
        return table(resource).get(id);
    }

    public Entity save(String resource, Entity entity) {
        if (entity.id == null) {
            long id = counters.getOrDefault(resource, 0L) + 1;
            counters.put(resource, id);
            entity.id = id;
        }
        table(resource).put(entity.id, entity);
        return entity;
    }

    public void delete(String resource, Long id) {
        table(resource).remove(id);
    }

    private Map<Long, Entity> table(String resource) {
        return storage.computeIfAbsent(resource, key -> new LinkedHashMap<>());
    }
}
