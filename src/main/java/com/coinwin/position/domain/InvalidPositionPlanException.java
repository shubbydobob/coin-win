package com.coinwin.position.domain;

import com.coinwin.common.domain.DomainException;

/**
 * 값은 각각 유효하지만 계획으로는 성립하지 않을 때 던진다.
 *
 * <p>예: 롱인데 손절가가 진입가보다 높다, 분할 비중의 합이 100% 가 아니다.
 * 값 자체가 부적절한 경우(null, 음수)는 {@code InvalidValueException} 이 맡는다.
 */
public class InvalidPositionPlanException extends DomainException {

    private static final long serialVersionUID = 1L;

    public InvalidPositionPlanException(String message) {
        super(message);
    }
}
