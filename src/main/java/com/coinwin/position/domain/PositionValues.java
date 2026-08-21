package com.coinwin.position.domain;

import com.coinwin.common.domain.InvalidValueException;

/**
 * null 검사를 도메인 예외로 바꾼다.
 *
 * <p>{@code Objects.requireNonNull} 은 {@code NullPointerException} 을 던지고, 그것은
 * 어드바이스에서 도메인 예외로 잡히지 않아 500 이 된다. 잘못된 요청은 400 이어야 한다.
 */
final class PositionValues {

    private PositionValues() {
    }

    static <T> T required(T value, String label) {
        if (value == null) {
            throw new InvalidValueException(label + "은(는) null 일 수 없다");
        }
        return value;
    }
}
