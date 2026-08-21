package com.coinwin.backtest.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import java.time.Instant;

/**
 * 한 봉이 닫힌 시점에 <b>알 수 있는 것 전부.</b>
 *
 * <p>이 타입이 룩어헤드를 막는 경계다. 전략은 캔들 목록을 통째로 받지 않고 이 스냅샷만 본다 —
 * 미래를 참조할 통로 자체가 없다. 스냅샷을 만드는 쪽(엔진)이 확정된 피벗만 넣고 그 시점의
 * 지표 값만 넣을 책임을 진다.
 *
 * @param at 이 봉의 시각. 주문은 <b>다음 봉부터</b> 유효하다
 * @param close 종가. 대의 역할과 근단 거리가 이 값으로 정해진다
 * @param atr 이 시점의 ATR. 대 폭·군집 허용치·손절 버퍼의 단위
 * @param zones 이 시점에 유효한 대 전체
 */
public record MarketSnapshot(Instant at, Price close, Money atr, ZoneMap zones) {

    public MarketSnapshot {
        DomainValues.required(at, "봉 시각");
        DomainValues.required(close, "종가");
        DomainValues.required(atr, "ATR");
        DomainValues.required(zones, "대 목록");
    }
}
