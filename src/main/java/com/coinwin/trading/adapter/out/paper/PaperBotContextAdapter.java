package com.coinwin.trading.adapter.out.paper;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.market.domain.Symbol;
import com.coinwin.trading.application.port.out.LoadBotContextPort;
import com.coinwin.trading.domain.BotContext;
import com.coinwin.trading.domain.PaperLedger;

/**
 * 시장은 진짜 거래소에서, 계좌는 장부에서.
 *
 * <p><b>시장을 읽은 값으로 장부를 먼저 전진시킨다.</b> 순서가 뒤집히면 이번 사이클의 전략이
 * <b>이미 손절에 닿은 포지션을 아직 열려 있는 것으로 보고</b> 판단한다. 진짜 거래소에서는
 * 시장이 알아서 체결하므로 이 한 줄이 그 역할을 대신한다.
 *
 * <p>감싸는 쪽({@code delegate})이 시장만 읽는다. 장부 모드와 실계좌 모드가 <b>시장을 읽는
 * 코드를 나눠 갖게</b> 하려는 것이고, 그래야 두 모드가 같은 값을 본다.
 */
public class PaperBotContextAdapter implements LoadBotContextPort {

    private final LoadBotContextPort marketOnly;
    private final PaperBrokerAdapter broker;

    public PaperBotContextAdapter(LoadBotContextPort marketOnly, PaperBrokerAdapter broker) {
        this.marketOnly = DomainValues.required(marketOnly, "시장 어댑터");
        this.broker = DomainValues.required(broker, "장부 브로커");
    }

    /**
     * <b>판독은 감싸는 쪽이 읽은 것을 그대로 나른다.</b> 여기서 다시 읽으면 한 사이클 안에서
     * 시장을 두 번 보게 되고, 그 둘이 어긋난 위에서 내린 판단은 재현되지 않는다.
     */
    @Override
    public BotContext contextFor(Symbol symbol) {
        BotContext market = marketOnly.contextFor(symbol);
        broker.advanceTo(market.view().mark());
        PaperLedger ledger = broker.ledger();
        return new BotContext(market.view(), market.reading(),
                ledger.state(), ledger.position(), broker.restingOrders());
    }
}
