package com.coinwin.common.domain;

import java.math.BigDecimal;

/**
 * 비율. 스케일 4, HALF_UP, 음수 금지.
 *
 * <p>100 을 100% 로 표현한다 (0.01 이 아니라). 분할 진입 비중과 리스크 비율에 쓴다.
 */
public record Percentage(BigDecimal value) {

    private static final int SCALE = 4;
    private static final String LABEL = "비율";
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public Percentage {
        value = DecimalValues.normalizeNonNegative(value, SCALE, LABEL);
    }

    public static Percentage of(BigDecimal value) {
        return new Percentage(value);
    }

    public static Percentage of(String value) {
        return new Percentage(DecimalValues.parse(value, LABEL));
    }

    /**
     * 금액에 비율을 적용한다. 결과는 금액 스케일(2)에서 반올림된다.
     *
     * <p>{@code riskAmount = balance × riskPercent} — 포지션 사이징의 첫 단계.
     */
    public Money applyTo(Money amount) {
        return Money.of(amount.value()
                .multiply(value)
                .divide(HUNDRED, Money.SCALE, DecimalValues.ROUNDING));
    }
}
