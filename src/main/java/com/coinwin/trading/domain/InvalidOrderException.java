package com.coinwin.trading.domain;

import com.coinwin.common.domain.InvalidValueException;

/**
 * 주문으로 성립하지 않는 값.
 *
 * <p>프레임워크 의존이 없다 — HTTP 매핑은 {@code @RestControllerAdvice} 한 곳에서만 한다.
 */
public class InvalidOrderException extends InvalidValueException {

    private static final long serialVersionUID = 1L;

    public InvalidOrderException(String message) {
        super(message);
    }
}
