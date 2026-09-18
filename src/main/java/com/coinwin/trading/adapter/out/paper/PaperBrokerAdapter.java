package com.coinwin.trading.adapter.out.paper;

import com.coinwin.backtest.domain.CostModel;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import com.coinwin.trading.application.port.out.PlaceOrderPort;
import com.coinwin.trading.domain.OrderId;
import com.coinwin.trading.domain.OrderIntent;
import com.coinwin.trading.domain.PaperLedger;
import com.coinwin.trading.domain.PlacedOrder;
import com.coinwin.trading.domain.TradingMode;
import java.time.Clock;
import java.util.List;

/**
 * 거래소 대신 장부에 적는 브로커. <b>봇의 기본 모드다.</b>
 *
 * <p>같은 루프·같은 판단·같은 {@link OrderIntent} 가 여기까지 오고, 마지막 한 칸에서만
 * 갈린다. 그래서 장부에서 도는 것을 확인하면 <b>실계좌에서 달라지는 것은 체결뿐</b>이다.
 *
 * <p><b>백테스트와 같은 {@link CostModel} 을 쓴다.</b> 모의 기록과 백테스트 결과를 나란히
 * 놓는 것이 이 봇의 검증 방법이므로({@code trading-bot.md} § 5의 C) 두 곳이 다른 자로 재면
 * 그 대조가 무의미해진다.
 *
 * <p><b>지정가를 다루지 않는다.</b> 봉 안에서 지정가가 닿았는지는 OHLC 로 알 수 없고,
 * 추측해서 체결하면 장부가 실제보다 좋게 나온다. 봇이 내는 주문은 시장가와 트리거뿐이다.
 */
public class PaperBrokerAdapter implements PlaceOrderPort {

    private final PaperExchange exchange;

    public PaperBrokerAdapter(CostModel costs, Clock clock, Money startingEquity) {
        this.exchange = new PaperExchange(costs, clock, startingEquity);
    }

    @Override
    public TradingMode mode() {
        return TradingMode.PAPER;
    }

    @Override
    public PlacedOrder place(OrderIntent intent) {
        return exchange.place(intent);
    }

    /** 없는 주문을 지우는 것은 실패가 아니다 — 이미 없다는 원하던 상태다. */
    @Override
    public void cancel(OrderId id) {
        exchange.cancel(id);
    }

    /**
     * 표시가가 여기까지 왔다고 알린다. 걸려 있던 트리거가 닿았으면 체결된다.
     *
     * <p>포트에 없는 이유는 <b>진짜 거래소에는 이런 메서드가 없기</b> 때문이다 — 거기서는
     * 시장이 알아서 체결한다. 부르는 쪽은 장부용 컨텍스트 어댑터 하나뿐이다.
     */
    public void advanceTo(Price mark) {
        exchange.advanceTo(mark);
    }

    /** 장부의 지금. 포지션과 실현 손익이 여기 있다. */
    public PaperLedger ledger() {
        return exchange.ledger();
    }

    /** 걸려 있는 트리거 주문. 포트에는 없다 — 검사와 화면을 위한 자리다. */
    public List<PlacedOrder> restingOrders() {
        return exchange.restingOrders();
    }
}
