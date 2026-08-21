package com.coinwin.backtest.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.position.domain.Direction;
import java.time.Instant;

/**
 * 다음 봉부터 유효한 지정가 주문. 신호와 <b>그 시점에 확정된 수량</b>을 함께 들고 있다.
 *
 * <p>수량을 무장 시점에 굳히는 이유는 사이징 입력(잔고)이 거래가 끝날 때마다 바뀌기 때문이다.
 * 체결 시점에 다시 계산하면 1차와 2차의 수량이 서로 다른 잔고에서 나와, 회차별 수량의 합이
 * 계획의 총수량과 어긋난다.
 *
 * @param signal 전략이 낸 계획
 * @param totalQuantity 전량 체결 기준 수량
 * @param plannedAt 신호가 선 봉의 시각. 주문은 그 다음 봉부터 유효하다
 */
record ArmedOrder(TradeSignal signal, Quantity totalQuantity, Instant plannedAt) {

    ArmedOrder {
        DomainValues.required(signal, "진입 신호");
        DomainValues.required(totalQuantity, "총수량");
        DomainValues.required(plannedAt, "계획 시각");
    }

    Direction direction() {
        return signal.direction();
    }

    Price firstEntryPrice() {
        return signal.plan().entries().entries().getFirst().price();
    }

    /** 회차별 수량. 총수량에 비중을 적용한다 — 회차마다 다시 사이징하지 않는다. */
    Quantity sliceQuantity(int index) {
        return signal.plan().entries().entries().get(index).allocation().applyTo(totalQuantity);
    }

    OpenPosition openAt(Price price, Instant at, CostModel costs) {
        return OpenPosition.opened(this, price, at, costs);
    }
}
