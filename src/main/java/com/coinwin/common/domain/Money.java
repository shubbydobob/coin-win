package com.coinwin.common.domain;

import java.math.BigDecimal;

/**
 * 금액. USDT 기준 스케일 2, HALF_UP.
 *
 * <p>네 값 객체 중 <b>Money 만 음수를 허용한다.</b> 실현손익을 표현해야 하기 때문이다.
 * 가격·수량·비율에는 음수가 존재하지 않는다.
 */
public record Money(BigDecimal value) {

    static final int SCALE = 2;
    private static final String LABEL = "금액";

    public Money {
        value = DecimalValues.normalize(value, SCALE, LABEL);
    }

    public static Money of(BigDecimal value) {
        return new Money(value);
    }

    public static Money of(String value) {
        return new Money(DecimalValues.parse(value, LABEL));
    }

    /**
     * 금액을 단가로 나눠 수량을 구한다. 포지션 사이징의 핵심 계산이다.
     *
     * <p>{@code quantity = riskAmount / perUnitLoss} — 사이즈가 아니라 손절가가 수량을 결정한다.
     *
     * @throws InvalidValueException 나누는 금액이 0 인 경우
     */
    public Quantity dividedBy(Money divisor) {
        if (divisor.value.signum() == 0) {
            throw new InvalidValueException("0 으로 나눌 수 없다");
        }
        return Quantity.of(value.divide(divisor.value, Quantity.SCALE, DecimalValues.ROUNDING));
    }

    /**
     * 배수를 적용한 금액. 레버리지 역수처럼 <b>무차원 배수</b>에만 쓴다.
     *
     * <p>{@code margin = notional × (1 / leverage)}
     */
    public Money multipliedBy(BigDecimal factor) {
        return Money.of(value.multiply(factor));
    }

    public boolean isGreaterThan(Money other) {
        return value.compareTo(other.value) > 0;
    }
}
