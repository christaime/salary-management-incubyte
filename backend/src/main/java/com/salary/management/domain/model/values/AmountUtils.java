package com.salary.management.domain.model.values;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class AmountUtils {
    public static int AMOUNT_SCALE = 2;

    public static BigDecimal nullToZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    public static BigDecimal scaleNullToZero(BigDecimal value) {
        return nullToZero(value).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }
}
