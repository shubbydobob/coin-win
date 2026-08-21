package com.coinwin.position.domain;

import com.coinwin.common.domain.DomainException;
import com.coinwin.common.domain.Price;

/**
 * 손절가가 청산가 너머에 있을 때 던진다.
 *
 * <p>이 상태의 계획은 손절 주문이 체결되기 전에 청산이 먼저 일어난다. 즉 계산된 최대손실이
 * 거짓이 된다 — 실제 손실은 증거금 전액이다. 리스크를 미리 보여주는 것이 이 도구의 목적이므로
 * 경고가 아니라 거부다.
 */
public class StopBeyondLiquidationException extends DomainException {

    private static final long serialVersionUID = 1L;

    public StopBeyondLiquidationException(int filledEntries, Price stopLoss, Price liquidationPrice) {
        super("%d건 체결 상태의 청산가(%s)가 손절가(%s)보다 먼저 닿는다. 손절이 작동하지 못한다"
                .formatted(filledEntries, liquidationPrice.value().toPlainString(),
                        stopLoss.value().toPlainString()));
    }
}
