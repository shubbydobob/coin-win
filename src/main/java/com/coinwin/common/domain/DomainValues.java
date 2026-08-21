package com.coinwin.common.domain;

import java.math.BigDecimal;

/**
 * 도메인 인자 검사. 프레임워크 없이 도메인 예외만 던진다.
 *
 * <p>{@code Objects.requireNonNull} 은 {@code NullPointerException} 을 던지고, 그것은
 * {@code DomainExceptionHandler} 가 잡지 못해 500 이 된다. 잘못된 요청은 400 이어야 한다.
 *
 * <p>같은 검사가 {@code position.domain} 과 {@code projection.domain} 두 곳에서 필요해져
 * {@code common} 으로 올렸다. conventions.md: "같은 로직이 두 번째 나타나면 즉시 추출한다."
 */
public final class DomainValues {

    private DomainValues() {
    }

    public static <T> T required(T value, String label) {
        if (value == null) {
            throw new InvalidValueException(label + "은(는) null 일 수 없다");
        }
        return value;
    }

    /**
     * 하한이 있는 정수 검사. 거래 수·시행 횟수처럼 0 이나 음수가 계산 자체를 성립시키지 않는
     * 값에 쓴다.
     */
    public static int atLeast(int value, int minimum, String label) {
        if (value < minimum) {
            throw new InvalidValueException(
                    label + "은(는) " + minimum + " 이상이어야 한다: " + value);
        }
        return value;
    }

    /**
     * 문자열을 {@link BigDecimal} 로 읽는다. 형식 오류는 도메인 예외가 된다.
     *
     * <p>{@code common} 의 네 값 객체는 {@code DecimalValues} 를 직접 쓰지만 그것은 패키지
     * 전용이다. {@code market.domain} 의 {@code FundingRate} 처럼 <b>다른 모듈이 자기 스케일
     * 정책을 가진 값 객체</b>를 만들 때 파싱만 다시 구현하지 않도록 이 두 메서드를 연다.
     */
    public static BigDecimal decimal(String value, String label) {
        return DecimalValues.parse(value, label);
    }

    /** null 검사 + 스케일 정규화. 반올림은 HALF_UP 하나뿐이다. 음수를 허용한다. */
    public static BigDecimal scaled(BigDecimal value, int scale, String label) {
        return DecimalValues.normalize(value, scale, label);
    }
}
