package ru.student.api.model;

import java.math.BigDecimal;
import java.util.List;

public final class CollectionPoint extends Entity {
    public String address;
    public List<Long> acceptedTypeIds;
    public Boolean available;
    public BigDecimal dailyMassLimit;
}
