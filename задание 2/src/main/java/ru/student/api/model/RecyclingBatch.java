package ru.student.api.model;

import java.math.BigDecimal;
import java.time.Instant;

public final class RecyclingBatch extends Entity {
    public Long userId;
    public Long pointId;
    public Long typeId;
    public BigDecimal mass;
    public Instant receivedAt;
    public String status;
}
