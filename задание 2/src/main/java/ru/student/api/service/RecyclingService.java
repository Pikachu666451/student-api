package ru.student.api.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.student.api.model.*;
import ru.student.api.repository.RecyclingRepository;

@Service
public class RecyclingService {
    private static final Set<String> RESOURCES = Set.of(
            "recycling-types", "collection-points", "users", "recycling-batches", "bonus-entries");
    private final RecyclingRepository repository;
    public RecyclingService(RecyclingRepository repository) {
        this.repository = repository;
    }

    public synchronized List<Entity> findAll(String resource) {
        checkResource(resource);
        return repository.findAll(resource);
    }

    public synchronized Entity findById(String resource, Long id) {
        checkResource(resource);
        Entity entity = repository.findById(resource, id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Запись не найдена");
        }
        return entity;
    }

    public synchronized Entity create(String resource, Entity entity) {
        checkResource(resource);
        if (entity instanceof RecyclingBatch batch) {
            batch.receivedAt = Instant.now();
        }
        validate(entity);
        return repository.save(resource, entity);
    }

    public synchronized Entity update(String resource, Long id, Entity entity) {
        Entity old = findById(resource, id);
        entity.id = old.id;
        if (entity instanceof RecyclingBatch batch) {
            batch.receivedAt = ((RecyclingBatch) old).receivedAt;
        }
        validate(entity);
        repository.save(resource, entity);
        refreshBonuses();
        return entity;
    }

    public synchronized void delete(String resource, Long id) {
        findById(resource, id);
        if (resource.equals("users") || resource.equals("collection-points") || resource.equals("recycling-types")) {
            for (Entity entity : repository.findAll("recycling-batches")) {
                RecyclingBatch batch = (RecyclingBatch) entity;
                if ((resource.equals("users") && batch.userId.equals(id))
                        || (resource.equals("collection-points") && batch.pointId.equals(id))
                        || (resource.equals("recycling-types") && batch.typeId.equals(id))) {
                    delete("recycling-batches", batch.id);
                }
            }
        }
        if (resource.equals("recycling-types")) {
            for (Entity entity : repository.findAll("collection-points")) {
                CollectionPoint point = (CollectionPoint) entity;
                point.acceptedTypeIds.removeIf(typeId -> typeId.equals(id));
            }
        }
        if (resource.equals("recycling-batches")) {
            for (Entity entity : repository.findAll("bonus-entries")) {
                BonusEntry bonus = (BonusEntry) entity;
                if (bonus.batchId.equals(id)) {
                    repository.delete("bonus-entries", bonus.id);
                }
            }
        }
        repository.delete(resource, id);
    }

    private void checkResource(String resource) {
        if (!RESOURCES.contains(resource)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Неизвестный адрес");
        }
    }

    private void validate(Entity entity) {
        if (entity instanceof User user) {
            require(hasText(user.name), "Укажите имя");
        } else if (entity instanceof RecyclingType type) {
            require(hasText(type.name) && hasText(type.unit), "Укажите название и единицу учёта");
            require(type.pointsPerKg != null && type.pointsPerKg.signum() >= 0, "Баллы не могут быть отрицательными");
        } else if (entity instanceof CollectionPoint point) {
            require(hasText(point.address), "Укажите адрес");
            require(point.available != null && point.acceptedTypeIds != null, "Укажите доступность и виды вторсырья");
            require(point.dailyMassLimit != null && point.dailyMassLimit.signum() > 0, "Лимит должен быть больше нуля");
            for (Long typeId : point.acceptedTypeIds) {
                linked("recycling-types", typeId);
            }
            require(point.dailyMassLimit.compareTo(dailyMass(point.id, null)) >= 0, "Лимит меньше уже принятой массы");
            for (Entity item : repository.findAll("recycling-batches")) {
                RecyclingBatch batch = (RecyclingBatch) item;
                if (point.id != null && point.id.equals(batch.pointId)) {
                    require(point.acceptedTypeIds.contains(batch.typeId), "Этот вид уже используется в партии");
                }
            }
        } else if (entity instanceof RecyclingBatch batch) {
            linked("users", batch.userId);
            CollectionPoint point = (CollectionPoint) linked("collection-points", batch.pointId);
            linked("recycling-types", batch.typeId);
            require(batch.mass != null && batch.mass.signum() > 0, "Масса должна быть больше нуля");
            if (batch.status == null) {
                batch.status = "ACCEPTED";
            }
            require(Set.of("ACCEPTED", "CANCELLED").contains(batch.status), "Неизвестный статус партии");
            require(point.acceptedTypeIds.contains(batch.typeId), "Пункт не принимает этот вид вторсырья");
            if (batch.status.equals("ACCEPTED")) {
                require(point.available, "Пункт недоступен");
                if (batch.receivedAt.atZone(ZoneOffset.UTC).toLocalDate().equals(LocalDate.now(ZoneOffset.UTC))) {
                    BigDecimal mass = dailyMass(batch.pointId, batch.id).add(batch.mass);
                    require(mass.compareTo(point.dailyMassLimit) <= 0, "Превышен суточный лимит массы");
                }
            }
        } else if (entity instanceof BonusEntry bonus) {
            linked("recycling-batches", bonus.batchId);
            if (bonus.status == null) {
                bonus.status = "PENDING";
            }
            require(Set.of("PENDING", "CREDITED", "CANCELLED").contains(bonus.status), "Неизвестный статус начисления");
            for (Entity item : repository.findAll("bonus-entries")) {
                BonusEntry other = (BonusEntry) item;
                if (other.batchId.equals(bonus.batchId) && !other.id.equals(bonus.id)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Для партии уже есть бонусная запись");
                }
            }
            calculateBonus(bonus);
        }
    }

    private Entity linked(String resource, Long id) {
        require(id != null, "Не указан id связанной записи");
        Entity entity = repository.findById(resource, id);
        require(entity != null, "Связанная запись не найдена: " + resource);
        return entity;
    }

    private BigDecimal dailyMass(Long pointId, Long excludedId) {
        BigDecimal total = BigDecimal.ZERO;
        for (Entity entity : repository.findAll("recycling-batches")) {
            RecyclingBatch batch = (RecyclingBatch) entity;
            if (batch.pointId.equals(pointId) && !batch.id.equals(excludedId)
                    && batch.status.equals("ACCEPTED")
                    && batch.receivedAt.atZone(ZoneOffset.UTC).toLocalDate().equals(LocalDate.now(ZoneOffset.UTC))) {
                total = total.add(batch.mass);
            }
        }
        return total;
    }

    private void calculateBonus(BonusEntry bonus) {
        RecyclingBatch batch = (RecyclingBatch) linked("recycling-batches", bonus.batchId);
        RecyclingType type = (RecyclingType) linked("recycling-types", batch.typeId);
        if (batch.status.equals("CANCELLED") || bonus.status.equals("CANCELLED")) {
            bonus.points = BigDecimal.ZERO;
            bonus.status = "CANCELLED";
        } else {
            bonus.points = batch.mass.multiply(type.pointsPerKg);
        }
    }

    private void refreshBonuses() {
        for (Entity entity : repository.findAll("bonus-entries")) {
            calculateBonus((BonusEntry) entity);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }
}
