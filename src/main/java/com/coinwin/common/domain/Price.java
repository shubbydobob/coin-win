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

    /**
     * 두 가격의 간격. 1단위(1 BTC)당 금액이므로 {@link Money} 다.
     *
     * <p>{@code |평단 - 손절가|} 가 포지션 사이징의 분모다. 부호는 방향이 결정하므로
     * 여기서는 절대값만 낸다.
     */
    public Money absoluteDifference(Price other) {
        return Money.of(value.subtract(other.value).abs());
    }

    /** 가격은 1단위의 금액이다. 명목가 계산에서 수량과 곱하기 위해 변환한다. */
    public Money asAmount() {
        return Money.of(value);
    }

    /**
     * 배수를 적용한 가격. 청산가 계수처럼 <b>무차원 배수</b>에만 쓴다.
     *
     * <p>금액이나 비율을 곱하는 용도가 아니다. 그런 계산은 각각 {@link Quantity#times}
     * 와 {@link Percentage#applyTo} 가 맡는다.
     */
    public Price multipliedBy(BigDecimal factor) {
        return Price.of(value.multiply(factor));
    }

    /**
     * 가격 거리만큼 올린 가격. 손절 버퍼와 슬리피지가 이 형태다.
     *
     * <p>{@link Money} 를 받는 이유는 {@link #absoluteDifference} 가 돌려주는 것이 그것이기
     * 때문이다. 가격에 가격을 더하는 것은 뜻이 없고, 가격에 <b>가격 거리</b>를 더하는 것은
     * 뜻이 있다.
     */
    public Price plus(Money distance) {
        DomainValues.required(distance, "가격 간격");
        return Price.of(value.add(distance.value()));
    }

    /**
     * 가격 거리만큼 내린 가격.
     *
     * <p>결과가 0 아래면 생성자가 거부한다. 손절가가 0 이하로 나오는 계획은 성립하지 않으므로
     * 조용히 0 으로 깎지 않는다.
     */
    public Price minus(Money distance) {
        DomainValues.required(distance, "가격 간격");
        return Price.of(value.subtract(distance.value()));
    }

    public boolean isBelow(Price other) {
        return value.compareTo(other.value) < 0;
    }

    public boolean isAbove(Price other) {
        return value.compareTo(other.value) > 0;
    }
}
