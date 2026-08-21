package com.coinwin.projection.domain;

import com.coinwin.common.domain.DomainException;

/**
 * 값은 각각 유효하지만 시뮬레이션 조건으로는 성립하지 않을 때 던진다.
 *
 * <p>예: 주당 거래 수와 기간은 각각 멀쩡한데 곱한 총 거래 수가 상한을 넘는다,
 * 점이 하나도 없는 자산 곡선이다. 값 자체가 부적절한 경우(null, 음수, 범위 초과)는
 * {@code InvalidValueException} 이 맡는다 — 그쪽은 400, 이쪽은 422 다.
 */
public class InvalidProjectionException extends DomainException {

    private static final long serialVersionUID = 1L;

    public InvalidProjectionException(String message) {
        super(message);
    }
}
