package com.coinwin.market.adapter.out.binance;

import com.coinwin.common.domain.DomainException;

/**
 * 거래소 응답이 기대한 형식이 아닐 때 던진다.
 *
 * <p>{@code ExternalDataUnavailableException}(닿지 않음)과 구분하는 이유는 원인이 전혀
 * 다르기 때문이다. 이쪽은 다시 시도해도 똑같이 실패한다 — 응답 형식이 바뀌었거나 우리가
 * 잘못 읽고 있는 것이고, 사람이 코드를 고쳐야 한다.
 */
public class BinanceResponseException extends DomainException {

    private static final long serialVersionUID = 1L;

    public BinanceResponseException(String message) {
        super(message);
    }
}
