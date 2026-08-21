package com.coinwin.common.domain;

/**
 * 값 객체가 도메인 규칙을 만족하지 않을 때 던진다.
 *
 * <p>도메인 예외이므로 프레임워크에 의존하지 않는다. HTTP 매핑은 adapter 계층의
 * {@code @RestControllerAdvice} 한 곳에서만 한다.
 *
 * <p>이 예외는 <b>값 자체가 부적절한</b> 경우를 뜻한다 (null, 음수, 범위 밖). 값은 유효한데
 * 도메인 규칙을 어긴 경우는 각 모듈의 규칙 예외가 맡는다. 어드바이스가 400 과 422 를
 * 가르는 기준이 이 구분이다.
 */
public class InvalidValueException extends DomainException {

    private static final long serialVersionUID = 1L;

    public InvalidValueException(String message) {
        super(message);
    }
}
