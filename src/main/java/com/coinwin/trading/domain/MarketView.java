package com.coinwin.trading.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Price;
import com.coinwin.market.domain.Candle;
import com.coinwin.market.domain.Symbol;
import java.time.Instant;
import java.util.List;

/**
 * 한 사이클에 전략이 보는 시장의 전부.
 *
 * <p><b>전략이 스스로 거래소를 부르지 않는다.</b> 부르게 두면 전략마다 다른 순간의 값을 보고,
 * 같은 사이클 안에서 "지금 가격" 이 두 개가 된다. 한 번 읽어 넘기면 <b>판단이 재현된다</b> —
 * 같은 {@code MarketView} 를 먹이면 같은 주문이 나와야 하고, 그것이 모의 기록과 백테스트를
 * 대조할 수 있는 조건이다.
 *
 * @param symbol 종목
 * @param mark 표시가. <b>청산이 트리거되고 명목을 재는 값</b>이라 마지막 체결가를 쓰지 않는다
 * @param candles 최근 봉. 시간순이고 <b>마지막 봉은 아직 닫히지 않았을 수 있다</b> —
 *     지표를 계산하는 쪽이 그 사실을 알아야 한 칸 밀린 값을 쓰지 않는다
 * @param at 이 값을 읽은 시각. <b>사이클 안에서 시각은 이것 하나뿐이다</b>
 */
public record MarketView(Symbol symbol, Price mark, List<Candle> candles, Instant at) {

    public MarketView {
        DomainValues.required(symbol, "종목");
        DomainValues.required(mark, "표시가");
        DomainValues.required(candles, "캔들");
        DomainValues.required(at, "관측 시각");
        candles = List.copyOf(candles);
    }
}
