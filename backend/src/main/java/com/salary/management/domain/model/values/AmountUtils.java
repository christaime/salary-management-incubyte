package com.salary.management.domain.model.values;

import java.math.BigDecimal;

public class AmountUtils {
    public static int AMOUNT_SCALE = 2;

    public static BigDecimal nullToZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
