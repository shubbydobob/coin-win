package com.coinwin.common.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 값 객체 공통의 스케일·부호 정규화.
 *
 * <p>네 개 값 객체가 같은 정규화를 반복하므로 한 곳으로 모았다.
 * conventions.md: "같은 로직이 두 번째 나타나면 즉시 추출한다."
 *
 * <p>반올림은 전부 {@link RoundingMode#HALF_UP} 이다. 이 정책이 값 객체 밖으로 새어 나가면
 * 계산 결과가 갈라진다.
 */
final class DecimalValues {

    static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    static final BigDecimal HUNDRED = new BigDecimal("100");

    private DecimalValues() {
    }

    /**
     * 전체 중 일부가 차지하는 백분율. {@link Money#percentOf} 와 {@link Percentage#ofRatio} 가
     * 같은 식을 쓰므로 한 곳에 둔다.
     *
     * <p>곱한 뒤 나눈다. 나눈 뒤 곱하면 중간 반올림이 한 번 더 끼어 결과가 갈린다.
     */
    static BigDecimal ratio(BigDecimal part, BigDecimal whole, int scale) {
        if (whole.signum() == 0) {
            throw new InvalidValueException("0 을 전체로 한 비율은 성립하지 않는다");
        }
        return part.multiply(HUNDRED).divide(whole, scale, ROUNDING);
    }

    static BigDecimal normalize(BigDecimal value, int scale, String label) {
        if (value == null) {
            throw new InvalidValueException(label + "은(는) null 일 수 없다");
        }
        return value.setScale(scale, ROUNDING);
    }

    /**
     * 부호 검사는 반올림 <b>전</b>에 한다. 그렇지 않으면 -0.001 같은 값이 스케일 2에서
     * -0.00 이 되어 음수 검사를 빠져나간다.
     */
    static BigDecimal normalizeNonNegative(BigDecimal value, int scale, String label) {
        if (value == null) {
            throw new InvalidValueException(label + "은(는) null 일 수 없다");
        }
        if (value.signum() < 0) {
            throw new InvalidValueException(label + "은(는) 음수일 수 없다: " + value.toPlainString());
        }
        return value.setScale(scale, ROUNDING);
    }

    static BigDecimal parse(String value, String label) {
        if (value == null) {
            throw new InvalidValueException(label + "은(는) null 일 수 없다");
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new InvalidValueException(label + "의 형식이 올바르지 않다: " + value);
        }
    }
}
