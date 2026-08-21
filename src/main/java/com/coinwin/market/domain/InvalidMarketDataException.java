package com.coinwin.market.domain;

import com.coinwin.common.domain.DomainException;

/**
 * 값은 각각 유효하지만 시장 데이터로는 성립하지 않을 때 던진다.
 *
 * <p>예: 고가가 저가보다 낮다, 조회 구간의 끝이 시작보다 앞이다.
 * 값 자체가 부적절한 경우(null, 음수)는 {@code InvalidValueException} 이 맡는다.
 * 이 구분이 어드바이스에서 400 과 422 를 가른다.
 */
public class InvalidMarketDataException extends DomainException {

    private static final long serialVersionUID = 1L;

    public InvalidMarketDataException(String message) {
        super(message);
    }
}
