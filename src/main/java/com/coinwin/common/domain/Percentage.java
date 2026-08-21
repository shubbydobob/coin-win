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
        return Money.of(apply(amount.value(), Money.SCALE));
    }

    /**
     * 수량에 비율을 적용한다. 결과는 수량 스케일(8)에서 반올림된다.
     *
     * <p>분할 진입에서 {@code n} 건까지 체결됐을 때 실제로 들고 있는 수량이
     * {@code 총수량 × 체결비중} 이다.
     */
    public Quantity applyTo(Quantity quantity) {
        return Quantity.of(apply(quantity.value(), Quantity.SCALE));
    }

    /**
     * 100 기준 값을 1 기준 소수로 바꾼다. 0.4% → 0.004.
     *
     * <p>청산가 공식이 유지증거금률을 소수로 요구한다. 변환을 호출부마다 되풀이하면
     * 100 으로 나누는 자리가 흩어진다.
     */
    public BigDecimal asFraction() {
        return value.divide(HUNDRED, SCALE + 2, DecimalValues.ROUNDING);
    }

    public boolean isGreaterThan(Percentage other) {
        return value.compareTo(other.value) > 0;
    }

    private BigDecimal apply(BigDecimal amount, int scale) {
        return amount.multiply(value).divide(HUNDRED, scale, DecimalValues.ROUNDING);
    }
}
