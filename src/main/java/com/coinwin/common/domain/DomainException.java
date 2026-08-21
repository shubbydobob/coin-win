package com.coinwin.common.domain;

/**
 * 모든 도메인 예외의 뿌리.
 *
 * <p>존재 이유는 HTTP 매핑 지점을 하나로 유지하기 위해서다. {@code @RestControllerAdvice} 가
 * 모듈마다 늘어나는 예외 타입을 일일이 알아야 한다면, 공통 계층이 하위 모듈을 참조하게 되어
 * {@code common ← 모든 모듈} 의존 방향이 뒤집힌다. 어드바이스는 이 타입 하나만 알면 된다.
 *
 * <p>프레임워크에 의존하지 않는다. HTTP 상태 코드는 이 계층의 관심사가 아니다.
 */
public abstract class DomainException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    protected DomainException(String message) {
        super(message);
    }
}
