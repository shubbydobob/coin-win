package com.coinwin.market.domain;

import com.coinwin.common.domain.Money;

/**
 * 명목가가 마지막 구간의 상한을 넘을 때 던진다.
 *
 * <p>조용히 마지막 구간으로 떨어뜨리지 않는다. 그런 포지션은 거래소에서 애초에 열 수 없고,
 * 값을 하나 내주면 열 수 있는 계획인 것처럼 보인다. 계산 결과가 거짓이 되는 경우는 막는다.
 */
public class NotionalExceedsBracketsException extends InvalidMarketDataException {

    private static final long serialVersionUID = 1L;

    public NotionalExceedsBracketsException(Money notional, Money highestCap) {
        super("명목가가 마지막 레버리지 구간을 넘는다: %s (상한 %s)"
                .formatted(notional.value().toPlainString(), highestCap.value().toPlainString()));
    }
}
