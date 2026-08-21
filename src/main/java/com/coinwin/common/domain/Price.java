package com.coinwin.common.domain;

import java.math.BigDecimal;

/**
 * 가격. USDT 기준 스케일 2, HALF_UP, 음수 금지.
 *
 * <p>생성 시점에 스케일을 고정하므로 {@code Price.of("1.5")} 와 {@code Price.of("1.50")} 은
 * 동등하다. record 의 equals 는 {@link BigDecimal#equals} 를 쓰고 이것은 스케일에 민감하기
 * 때문에, 정규화하지 않으면 같은 값이 다른 값으로 취급된다.
 */
public record Price(BigDecimal value) {

    private static final int SCALE = 2;
    private static final String LABEL = "가격";

    public Price {
        value = DecimalValues.normalizeNonNegative(value, SCALE, LABEL);
    }

    public static Price of(BigDecimal value) {
        return new Price(value);
    }

    public static Price of(String value) {
        return new Price(DecimalValues.parse(value, LABEL));
    }
}
