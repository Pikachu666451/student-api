package ru.student.api.controller;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import ru.student.api.model.RecyclingBatch;
import ru.student.api.service.OperationsService;

@RestController
@RequestMapping("/api/operations")
public class OperationsController {
    private final OperationsService service;
    public OperationsController(OperationsService service) { this.service = service; }
    public record Acceptance(Long userId, Long pointId, Long typeId, BigDecimal mass) {}
    public record Transfer(Long pointId) {}
    @PostMapping("/accept")
    public Object accept(@RequestBody Acceptance input) {
        RecyclingBatch batch = new RecyclingBatch();
        batch.userId=input.userId(); batch.pointId=input.pointId();
        batch.typeId=input.typeId(); batch.mass=input.mass();
        return service.accept(batch);
    }
    @PostMapping("/batches/{id}/credit")
    public Object credit(@PathVariable Long id) { return service.credit(id); }
    @PostMapping("/batches/{id}/cancel")
    public Object cancel(@PathVariable Long id) { return service.cancel(id); }
    @PostMapping("/batches/{id}/transfer")
    public Object transfer(@PathVariable Long id, @RequestBody Transfer input) { return service.transfer(id,input.pointId()); }
    @GetMapping("/users/{id}/balance")
    public Object balance(@PathVariable Long id) { return service.balance(id); }
}
