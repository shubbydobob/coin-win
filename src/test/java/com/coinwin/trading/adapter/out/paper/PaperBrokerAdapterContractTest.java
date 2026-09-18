package com.coinwin.trading.adapter.out.paper;

import com.coinwin.backtest.domain.CostModel;
import com.coinwin.common.domain.Money;
import com.coinwin.trading.application.port.out.PlaceOrderPort;
import com.coinwin.trading.application.port.out.PlaceOrderPortContract;
import java.time.Clock;

/**
 * 장부 브로커가 <b>거래소 어댑터와 같은 계약</b>을 지키는가.
 *
 * <p>이 포트에서 계약 테스트의 값이 가장 크다 — 장부에서 되던 것이 실계좌에서 다르게
 * 동작하면 그 차이는 돈으로 드러난다.
 */
class PaperBrokerAdapterContractTest extends PlaceOrderPortContract {

    private final PaperBrokerAdapter broker = new PaperBrokerAdapter(
            CostModel.free(), Clock.systemUTC(), Money.of("800"));

    @Override
    protected PlaceOrderPort port() {
        return broker;
    }
}
