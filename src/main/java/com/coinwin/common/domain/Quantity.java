package com.coinwin.common.domain;

import java.math.BigDecimal;

/**
 * 수량. BTC 기준 스케일 8, HALF_UP, 음수 금지.
 *
 * <p>스케일 8 은 BTC 의 최소 단위(사토시)에 맞춘 것이다.
 */
public record Quantity(BigDecimal value) {

    static final int SCALE = 8;
    private static final String LABEL = "수량";

    public Quantity {
        value = DecimalValues.normalizeNonNegative(value, SCALE, LABEL);
    }

    public static Quantity of(BigDecimal value) {
        return new Quantity(value);
    }

    public static Quantity of(String value) {
        return new Quantity(DecimalValues.parse(value, LABEL));
    }
}
