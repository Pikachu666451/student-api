package ru.student.api.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import ru.student.api.model.*;

@Repository
public class RecyclingRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Map<String, Class<? extends Entity>> types = Map.of(
        "users", User.class, "recycling-types", RecyclingType.class,
        "collection-points", CollectionPoint.class, "recycling-batches", RecyclingBatch.class,
        "bonus-entries", BonusEntry.class);
    public RecyclingRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }
    public void lock() {
        jdbc.queryForObject("SELECT id FROM app_lock WHERE id=1 FOR UPDATE", Integer.class);
    }
    private String table(String resource) {
        if (!types.containsKey(resource)) throw new IllegalArgumentException("Неизвестная таблица");
        return resource.replace('-', '_');
    }
    public List<Entity> findAll(String resource) {
        return jdbc.query("SELECT * FROM " + table(resource) + " ORDER BY id", (row, number) -> {
            Map<String, Object> data = new LinkedHashMap<>();
            for (Field field : types.get(resource).getFields()) {
                if (field.getName().equals("acceptedTypeIds")) continue;
                Object value = row.getObject(field.getName());
                if (value instanceof OffsetDateTime time) value = time.toInstant().toString();
                data.put(field.getName(), value);
            }
            Entity entity = mapper.convertValue(data, types.get(resource));
            if (entity instanceof CollectionPoint point) {
                point.acceptedTypeIds = jdbc.queryForList("SELECT typeId FROM point_types WHERE pointId=? ORDER BY typeId", Long.class, point.id);
            }
            return entity;
        });
    }
    public Entity findById(String resource, Long id) {
        for (Entity entity : findAll(resource)) if (entity.id.equals(id)) return entity;
        return null;
    }
    public Entity save(String resource, Entity entity) {
        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        try {
            for (Field field : entity.getClass().getFields()) {
                if (field.getName().equals("id") || field.getName().equals("acceptedTypeIds")) continue;
                columns.add(field.getName());
                Object value = field.get(entity);
                values.add(value instanceof Instant time ? OffsetDateTime.ofInstant(time, java.time.ZoneOffset.UTC) : value);
            }
        } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
        if (entity.id == null) {
            String sql = "INSERT INTO " + table(resource) + " (" + String.join(",",columns) + ") VALUES (" + String.join(",",Collections.nCopies(values.size(),"?")) + ")";
            GeneratedKeyHolder key = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                for (int i=0; i<values.size(); i++) statement.setObject(i+1,values.get(i));
                return statement;
            }, key);
            entity.id = key.getKey().longValue();
        } else {
            List<String> assignments = new ArrayList<>();
            for (String column : columns) assignments.add(column + "=?");
            values.add(entity.id);
            jdbc.update("UPDATE " + table(resource) + " SET " + String.join(",",assignments) + " WHERE id=?", values.toArray());
        }
        if (entity instanceof CollectionPoint point) {
            jdbc.update("DELETE FROM point_types WHERE pointId=?", point.id);
            for (Long id : new HashSet<>(point.acceptedTypeIds)) jdbc.update("INSERT INTO point_types VALUES(?,?)",point.id,id);
        }
        return entity;
    }
    public void delete(String resource, Long id) {
        jdbc.update("DELETE FROM " + table(resource) + " WHERE id=?",id);
    }
}
