package ru.student.api.service;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import ru.student.api.model.*;
import ru.student.api.repository.RecyclingRepository;

@Service
@Transactional
public class OperationsService {
    private final RecyclingRepository repository;
    private final RecyclingService service;
    public OperationsService(RecyclingRepository repository, RecyclingService service) {
        this.repository = repository;
        this.service = service;
    }
    public Map<String,Object> accept(RecyclingBatch batch) {
        repository.lock();
        batch.id = null;
        batch.status = "ACCEPTED";
        service.create("recycling-batches",batch);
        BonusEntry bonus = new BonusEntry();
        bonus.batchId = batch.id;
        service.create("bonus-entries",bonus);
        return Map.of("batch",batch,"bonus",bonus);
    }
    public BonusEntry credit(Long id) {
        repository.lock();
        RecyclingBatch batch = (RecyclingBatch) service.findById("recycling-batches",id);
        check(batch.status.equals("ACCEPTED"), "Партия отменена");
        BonusEntry bonus = bonus(id);
        check(bonus.status.equals("PENDING"), "Начисление уже обработано");
        bonus.status = "CREDITED";
        repository.save("bonus-entries",bonus);
        return bonus;
    }
    public Map<String,Object> cancel(Long id) {
        repository.lock();
        RecyclingBatch batch = (RecyclingBatch) service.findById("recycling-batches",id);
        check(batch.status.equals("ACCEPTED"), "Партия уже отменена");
        BonusEntry bonus = bonus(id);
        batch.status = "CANCELLED";
        bonus.status = "CANCELLED";
        bonus.points = BigDecimal.ZERO;
        repository.save("recycling-batches",batch);
        repository.save("bonus-entries",bonus);
        return Map.of("batch",batch,"bonus",bonus);
    }
    public RecyclingBatch transfer(Long id, Long pointId) {
        repository.lock();
        RecyclingBatch batch = (RecyclingBatch) service.findById("recycling-batches",id);
        check(batch.status.equals("ACCEPTED"), "Партия отменена");
        check(!batch.pointId.equals(pointId), "Партия уже находится в этом пункте");
        for (Entity entity : repository.findAll("bonus-entries")) {
            BonusEntry bonus = (BonusEntry) entity;
            if (bonus.batchId.equals(id)) check(bonus.status.equals("PENDING"), "Баллы уже обработаны");
        }
        batch.pointId = pointId;
        return (RecyclingBatch) service.update("recycling-batches",id,batch);
    }
    public Map<String,Object> balance(Long userId) {
        repository.lock();
        User user = (User) service.findById("users",userId);
        BigDecimal credited = BigDecimal.ZERO;
        BigDecimal pending = BigDecimal.ZERO;
        int batches = 0;
        for (Entity entity : repository.findAll("recycling-batches")) {
            RecyclingBatch batch = (RecyclingBatch) entity;
            if (!batch.userId.equals(userId) || !batch.status.equals("ACCEPTED")) continue;
            batches++;
            for (Entity entry : repository.findAll("bonus-entries")) {
                BonusEntry bonus = (BonusEntry) entry;
                if (!bonus.batchId.equals(batch.id)) continue;
                if (bonus.status.equals("CREDITED")) credited = credited.add(bonus.points);
                if (bonus.status.equals("PENDING")) pending = pending.add(bonus.points);
            }
        }
        return Map.of("user",user,"batches",batches,"credited",credited,"pending",pending);
    }
    private BonusEntry bonus(Long id) {
        for (Entity entity : repository.findAll("bonus-entries")) {
            BonusEntry bonus = (BonusEntry) entity;
            if (bonus.batchId.equals(id)) return bonus;
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT,"Для партии нет бонусной записи");
    }
    private void check(boolean condition, String message) {
        if (!condition) throw new ResponseStatusException(HttpStatus.CONFLICT,message);
    }
}
