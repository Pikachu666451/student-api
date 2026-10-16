package ru.student.api.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.student.api.model.*;
import ru.student.api.service.RecyclingService;

@RestController
@RequestMapping("/api")
public class RecyclingController {
    private final RecyclingService service;
    private final ObjectMapper mapper;

    public RecyclingController(RecyclingService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping("/{resource}")
    public List<Entity> findAll(@PathVariable String resource) {
        return service.findAll(resource);
    }

    @GetMapping("/{resource}/{id}")
    public Entity findById(@PathVariable String resource, @PathVariable Long id) {
        return service.findById(resource, id);
    }

    @PostMapping("/{resource}")
    public ResponseEntity<Entity> create(@PathVariable String resource,
                                         @RequestBody Map<String, Object> data) {
        checkFields(resource, data);
        Entity result = service.create(resource, toEntity(resource, data));
        return ResponseEntity.created(URI.create("/api/" + resource + "/" + result.id)).body(result);
    }

    @PatchMapping("/{resource}/{id}")
    public Entity update(@PathVariable String resource, @PathVariable Long id,
                         @RequestBody Map<String, Object> data) {
        checkFields(resource, data);
        Entity old = service.findById(resource, id);
        Map<String, Object> merged = mapper.convertValue(old, new TypeReference<>() {});
        merged.putAll(data);
        return service.update(resource, id, toEntity(resource, merged));
    }

    @DeleteMapping("/{resource}/{id}")
    public ResponseEntity<Void> delete(@PathVariable String resource, @PathVariable Long id) {
        service.delete(resource, id);
        return ResponseEntity.noContent().build();
    }

    private void checkFields(String resource, Map<String, Object> data) {
        if (data == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите данные");
        }
        Set<String> fields = switch (resource) {
            case "users" -> Set.of("name");
            case "recycling-types" -> Set.of("name", "unit", "pointsPerKg");
            case "collection-points" -> Set.of("address", "acceptedTypeIds", "available", "dailyMassLimit");
            case "recycling-batches" -> Set.of("userId", "pointId", "typeId", "mass", "status");
            case "bonus-entries" -> Set.of("batchId", "status");
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Неизвестный адрес");
        };
        for (String field : data.keySet()) {
            if (!fields.contains(field)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Нельзя передать поле: " + field);
            }
        }
    }

    private Entity toEntity(String resource, Map<String, Object> data) {
        try {
            return switch (resource) {
                case "users" -> mapper.convertValue(data, User.class);
                case "recycling-types" -> mapper.convertValue(data, RecyclingType.class);
                case "collection-points" -> mapper.convertValue(data, CollectionPoint.class);
                case "recycling-batches" -> mapper.convertValue(data, RecyclingBatch.class);
                case "bonus-entries" -> mapper.convertValue(data, BonusEntry.class);
                default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            };
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неверный формат данных");
        }
    }
}
