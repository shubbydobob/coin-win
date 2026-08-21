package com.coinwin.common.domain;

/**
 * 계산에 필요한 외부 입력을 얻지 못했을 때 던진다. 규칙 위반이 아니라 <b>입력의 부재</b>다.
 *
 * <p>{@link DomainException} 을 상속하는 이유는 HTTP 매핑 지점을 하나로 유지하기 위해서다.
 * 어드바이스가 이 타입을 따로 처리해 503 을 낸다 — 거래소가 닿지 않는 것은 요청이 잘못된
 * 것도(400) 계획이 성립하지 않는 것도(422) 아니다. 잠시 뒤 다시 하면 되는 일이다.
 *
 * <p>{@code common} 에 두는 이유는 모듈마다 같은 예외를 만들면 어드바이스가 모든 모듈을
 * 알아야 하고, 그러면 {@code common ← 모든 모듈} 의존 방향이 뒤집히기 때문이다.
 */
public class ExternalDataUnavailableException extends DomainException {

    private static final long serialVersionUID = 1L;

    public ExternalDataUnavailableException(String message, Throwable cause) {
        super(message);
        initCause(cause);
    }
}
