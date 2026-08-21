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

    /**
     * 총액 = 수량 × 단가.
     *
     * <p>명목가({@code 수량 × 평단})와 최대손실({@code 수량 × 1단위당 손실})이 같은 형태다.
     * 두 계산이 갈라지지 않도록 한 메서드로 둔다.
     */
    public Money times(Money unitAmount) {
        return Money.of(value.multiply(unitAmount.value()));
    }

    /**
     * 총액을 {@code parts} 등분한 금액. 반올림은 마지막에 한 번만 일어난다.
     *
     * <p>{@code 수량 × 단가} 를 먼저 {@link Money} 로 만든 뒤 나누면 스케일 2 로 스냅된 값을
     * 나누게 되어 결과가 1센트 어긋난다. 증거금({@code 명목가 / leverage})이 정확히 이 형태다.
     *
     * @throws InvalidValueException {@code parts} 가 0 인 경우
     */
    public Money times(Money unitAmount, int parts) {
        if (parts == 0) {
            throw new InvalidValueException("0 등분할 수 없다");
        }
        return Money.of(value.multiply(unitAmount.value())
                .divide(BigDecimal.valueOf(parts), Money.SCALE, DecimalValues.ROUNDING));
    }
}
