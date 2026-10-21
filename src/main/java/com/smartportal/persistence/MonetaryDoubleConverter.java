package com.smartportal.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Maps legacy Java Double properties to exact PostgreSQL NUMERIC(19,4) columns. */
@Converter
public class MonetaryDoubleConverter implements AttributeConverter<Double, BigDecimal> {
    private static final int SCALE = 4;

    @Override
    public BigDecimal convertToDatabaseColumn(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.HALF_UP);
    }

    @Override
    public Double convertToEntityAttribute(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}