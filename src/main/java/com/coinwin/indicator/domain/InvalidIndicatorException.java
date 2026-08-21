package com.coinwin.indicator.domain;

import com.coinwin.common.domain.DomainException;

/**
 * 값은 각각 유효하지만 지표로는 성립하지 않을 때 던진다.
 *
 * <p>예: 밴드 상단이 하단보다 낮다, 전환선 기간이 기준선 기간보다 길다.
 * 값 자체가 부적절한 경우(null, 음수)는 {@code InvalidValueException} 이 맡는다.
 */
public class InvalidIndicatorException extends DomainException {

    private static final long serialVersionUID = 1L;

    public InvalidIndicatorException(String message) {
        super(message);
    }
}
