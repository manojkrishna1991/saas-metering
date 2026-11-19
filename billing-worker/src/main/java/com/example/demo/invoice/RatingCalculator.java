package com.example.demo.invoice;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Exact decimal rating. Never uses double/float. */
public final class RatingCalculator {

    public static final int CURRENCY_SCALE = 2;
    public static final int RATE_SCALE = 6;

    private RatingCalculator() {
    }

    public static long billableUnits(long totalConsumed, long includedAllowance) {
        return Math.max(0L, totalConsumed - includedAllowance);
    }

    /** {@code billableUnits * overageRate}, rounded HALF_UP to currency scale. */
    public static BigDecimal metricCharge(long billableUnits, BigDecimal overageRate) {
        return BigDecimal.valueOf(billableUnits)
                .multiply(overageRate.setScale(RATE_SCALE, RoundingMode.HALF_UP))
                .setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);
    }
}
