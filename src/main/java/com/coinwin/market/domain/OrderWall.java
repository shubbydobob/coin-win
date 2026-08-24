package com.coinwin.market.domain;

import com.coinwin.common.domain.DomainValues;
import java.math.BigDecimal;

/**
 * 호가창에서 유난히 두꺼운 한 단 — 호가 매물대.
 *
 * <p><b>거래량 매물대와 전혀 다른 것이다.</b> 거래량 매물대는 <b>이미 체결된</b> 물량이 어느
 * 가격에 쌓였는지이고, 이것은 <b>아직 체결되지 않은</b> 주문이다. 그래서 성질이 정반대다 —
 * 체결된 것은 사라지지 않지만 <b>호가는 한순간에 취소된다.</b>
 *
 * <p><b>그래서 이 타입은 특히 아무것도 예측하지 않는다.</b> 큰 벽이 있다고 거기서 막힌다는
 * 뜻이 아니고, 오히려 체결시킬 생각 없이 세워 둔 미끼인 경우가 많다({@code OrderBook} 이
 * 불균형에 대해 적어 둔 것과 같은 이유). 답하는 것은 "지금 이 한 단에 평소의 몇 배가 걸려
 * 있는가" 까지다.
 *
 * <p><b>배수로 말한다.</b> 12 BTC 가 두꺼운지 얇은지는 그 자체로 알 수 없고, 같은 쪽 호가의
 * 평균과 견줘야 뜻이 생긴다 — 분위로 말하는 이상치 지표와 같은 판단이다.
 *
 * @param level 그 가격과 잔량
 * @param multipleOfAverage 같은 쪽 호가 한 단 평균의 몇 배인가
 */
public record OrderWall(PriceLevel level, BigDecimal multipleOfAverage) {

    public OrderWall {
        DomainValues.required(level, "호가 단");
        DomainValues.required(multipleOfAverage, "평균 대비 배수");
    }
}
