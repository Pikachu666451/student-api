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

@org.springframework.transaction.annotation.Transactional
@Service
public class RecyclingService {
    private static final Set<String> RESOURCES = Set.of(
            "recycling-types", "collection-points", "users", "recycling-batches", "bonus-entries");
    private final RecyclingRepository repository;
    public RecyclingService(RecyclingRepository repository) {
        this.repository = repository;
    }

    public List<Entity> findAll(String resource) {
        repository.lock();
        checkResource(resource);
        return repository.findAll(resource);
    }

    public Entity findById(String resource, Long id) {
        repository.lock();
        checkResource(resource);
        Entity entity = repository.findById(resource, id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Р—Р°РїРёСЃСЊ РЅРµ РЅР°Р№РґРµРЅР°");
        }
        return entity;
    }

    public Entity create(String resource, Entity entity) {
        repository.lock();
        checkResource(resource);
        if (entity instanceof RecyclingBatch batch) {
            batch.receivedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        }
        validate(entity);
        return repository.save(resource, entity);
    }

    public Entity update(String resource, Long id, Entity entity) {
        repository.lock();
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

    public void delete(String resource, Long id) {
        repository.lock();
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
                repository.save("collection-points", point);
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
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "РќРµРёР·РІРµСЃС‚РЅС‹Р№ Р°РґСЂРµСЃ");
        }
    }

    private void validate(Entity entity) {
        if (entity instanceof User user) {
            require(hasText(user.name), "РЈРєР°Р¶РёС‚Рµ РёРјСЏ");
        } else if (entity instanceof RecyclingType type) {
            require(hasText(type.name) && hasText(type.unit), "РЈРєР°Р¶РёС‚Рµ РЅР°Р·РІР°РЅРёРµ Рё РµРґРёРЅРёС†Сѓ СѓС‡С‘С‚Р°");
            require(type.pointsPerKg != null && type.pointsPerKg.signum() >= 0, "Р‘Р°Р»Р»С‹ РЅРµ РјРѕРіСѓС‚ Р±С‹С‚СЊ РѕС‚СЂРёС†Р°С‚РµР»СЊРЅС‹РјРё");
        } else if (entity instanceof CollectionPoint point) {
            require(hasText(point.address), "РЈРєР°Р¶РёС‚Рµ Р°РґСЂРµСЃ");
            require(point.available != null && point.acceptedTypeIds != null, "РЈРєР°Р¶РёС‚Рµ РґРѕСЃС‚СѓРїРЅРѕСЃС‚СЊ Рё РІРёРґС‹ РІС‚РѕСЂСЃС‹СЂСЊСЏ");
            require(point.dailyMassLimit != null && point.dailyMassLimit.signum() > 0, "Р›РёРјРёС‚ РґРѕР»Р¶РµРЅ Р±С‹С‚СЊ Р±РѕР»СЊС€Рµ РЅСѓР»СЏ");
            for (Long typeId : point.acceptedTypeIds) {
                linked("recycling-types", typeId);
            }
            require(point.dailyMassLimit.compareTo(dailyMass(point.id, null)) >= 0, "Р›РёРјРёС‚ РјРµРЅСЊС€Рµ СѓР¶Рµ РїСЂРёРЅСЏС‚РѕР№ РјР°СЃСЃС‹");
            for (Entity item : repository.findAll("recycling-batches")) {
                RecyclingBatch batch = (RecyclingBatch) item;
                if (point.id != null && point.id.equals(batch.pointId)) {
                    require(point.acceptedTypeIds.contains(batch.typeId), "Р­С‚РѕС‚ РІРёРґ СѓР¶Рµ РёСЃРїРѕР»СЊР·СѓРµС‚СЃСЏ РІ РїР°СЂС‚РёРё");
                }
            }
        } else if (entity instanceof RecyclingBatch batch) {
            linked("users", batch.userId);
            CollectionPoint point = (CollectionPoint) linked("collection-points", batch.pointId);
            linked("recycling-types", batch.typeId);
            require(batch.mass != null && batch.mass.signum() > 0, "РњР°СЃСЃР° РґРѕР»Р¶РЅР° Р±С‹С‚СЊ Р±РѕР»СЊС€Рµ РЅСѓР»СЏ");
            if (batch.status == null) {
                batch.status = "ACCEPTED";
            }
            require(Set.of("ACCEPTED", "CANCELLED").contains(batch.status), "РќРµРёР·РІРµСЃС‚РЅС‹Р№ СЃС‚Р°С‚СѓСЃ РїР°СЂС‚РёРё");
            require(point.acceptedTypeIds.contains(batch.typeId), "РџСѓРЅРєС‚ РЅРµ РїСЂРёРЅРёРјР°РµС‚ СЌС‚РѕС‚ РІРёРґ РІС‚РѕСЂСЃС‹СЂСЊСЏ");
            if (batch.status.equals("ACCEPTED")) {
                require(point.available, "РџСѓРЅРєС‚ РЅРµРґРѕСЃС‚СѓРїРµРЅ");
                if (batch.receivedAt.atZone(ZoneOffset.UTC).toLocalDate().equals(LocalDate.now(ZoneOffset.UTC))) {
                    BigDecimal mass = dailyMass(batch.pointId, batch.id).add(batch.mass);
                    require(mass.compareTo(point.dailyMassLimit) <= 0, "РџСЂРµРІС‹С€РµРЅ СЃСѓС‚РѕС‡РЅС‹Р№ Р»РёРјРёС‚ РјР°СЃСЃС‹");
                }
            }
        } else if (entity instanceof BonusEntry bonus) {
            linked("recycling-batches", bonus.batchId);
            if (bonus.status == null) {
                bonus.status = "PENDING";
            }
            require(Set.of("PENDING", "CREDITED", "CANCELLED").contains(bonus.status), "РќРµРёР·РІРµСЃС‚РЅС‹Р№ СЃС‚Р°С‚СѓСЃ РЅР°С‡РёСЃР»РµРЅРёСЏ");
            for (Entity item : repository.findAll("bonus-entries")) {
                BonusEntry other = (BonusEntry) item;
                if (other.batchId.equals(bonus.batchId) && !other.id.equals(bonus.id)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Р”Р»СЏ РїР°СЂС‚РёРё СѓР¶Рµ РµСЃС‚СЊ Р±РѕРЅСѓСЃРЅР°СЏ Р·Р°РїРёСЃСЊ");
                }
            }
            calculateBonus(bonus);
        }
    }

    private Entity linked(String resource, Long id) {
        require(id != null, "РќРµ СѓРєР°Р·Р°РЅ id СЃРІСЏР·Р°РЅРЅРѕР№ Р·Р°РїРёСЃРё");
        Entity entity = repository.findById(resource, id);
        require(entity != null, "РЎРІСЏР·Р°РЅРЅР°СЏ Р·Р°РїРёСЃСЊ РЅРµ РЅР°Р№РґРµРЅР°: " + resource);
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
            repository.save("bonus-entries", entity);
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
