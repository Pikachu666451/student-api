package ru.student.api.model;

import java.math.BigDecimal;

public final class BonusEntry extends Entity {
    public Long batchId;
    public BigDecimal points;
    public String status;
}
