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

    public boolean isGreaterThan(Money other) {
        return value.compareTo(other.value) > 0;
    }

    /**
     * 무차원 배수를 적용한 금액. 복리 자산 곡선의 각 점이 {@code 초기 자본 × 누적 배수} 다.
     *
     * <p>직전 점에 배수를 곱해 나가는 방식이 아니라 <b>원금에서 한 번에</b> 계산하라고 이
     * 메서드가 존재한다. 점마다 센트로 반올림한 값을 다시 곱하면 오차가 거래 수만큼 쌓인다.
     */
    public Money times(BigDecimal factor) {
        return Money.of(value.multiply(DomainValues.required(factor, "배수")));
    }

    /** 두 금액의 차. 결과는 음수일 수 있다 — 손실이 그렇다. */
    public Money minus(Money other) {
        return Money.of(value.subtract(DomainValues.required(other, "금액").value()));
    }

    /**
     * 전체 금액 대비 이 금액의 비율. 최대낙폭이 {@code (고점 - 현재) / 고점} 이다.
     *
     * @throws InvalidValueException 전체가 0 이거나 이 금액이 음수인 경우
     */
    public Percentage percentOf(Money whole) {
        return Percentage.of(DecimalValues.ratio(
                value, DomainValues.required(whole, "전체 금액").value, Percentage.SCALE));
    }
}
