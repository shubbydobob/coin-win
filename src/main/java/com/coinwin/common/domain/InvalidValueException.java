package com.coinwin.common.domain;

/**
 * 값 객체가 도메인 규칙을 만족하지 않을 때 던진다.
 *
 * <p>도메인 예외이므로 프레임워크에 의존하지 않는다. HTTP 매핑은 adapter 계층의
 * {@code @RestControllerAdvice} 한 곳에서만 한다.
 */
public class InvalidValueException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidValueException(String message) {
        super(message);
    }
}
