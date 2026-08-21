package com.coinwin.common.domain;

/**
 * 식별자로 지목한 것이 없을 때 던진다.
 *
 * <p>{@link InvalidValueException}(400) 과도 규칙 위반(422) 과도 다르다. 요청 자체는 흠이 없고
 * 가리키는 대상이 없을 뿐이므로 404 다. {@code common} 에 두는 이유는 어드바이스가 모듈별
 * 예외를 열거하면 의존 방향이 뒤집히기 때문이다 — {@link DomainException} 과 같은 이유다.
 */
public abstract class NotFoundException extends DomainException {

    private static final long serialVersionUID = 1L;

    protected NotFoundException(String message) {
        super(message);
    }
}
