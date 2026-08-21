package com.coinwin.journal.domain;

import com.coinwin.common.domain.DomainException;

/**
 * 값 하나하나는 멀쩡한데 <b>거래 기록으로 성립하지 않는</b> 경우.
 *
 * <p>{@code InvalidValueException}(400) 과 갈라 두는 기준은 {@code DomainExceptionHandler} 의
 * 것과 같다 — 음수 수수료는 값의 문제이고, 진입보다 앞선 청산 시각은 조합의 문제다. 후자가
 * 이 예외이며 422 가 된다.
 */
public class InvalidTradeException extends DomainException {

    private static final long serialVersionUID = 1L;

    public InvalidTradeException(String message) {
        super(message);
    }
}
