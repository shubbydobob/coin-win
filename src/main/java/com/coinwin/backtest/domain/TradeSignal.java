package com.coinwin.backtest.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.journal.domain.MarketContext;
import com.coinwin.position.domain.Direction;
import com.coinwin.position.domain.PositionPlan;

/**
 * 전략이 낸 진입 계획 하나. 아직 체결되지 않았다.
 *
 * <p>{@link MarketContext} 는 {@code journal/domain} 의 것을 그대로 쓴다. 백테스트가 만든 거래와
 * 실제로 한 매매가 <b>같은 어휘로 표현되는 것 자체가 값</b>이기 때문이다 — 검증한 전략과 실제
 * 기록을 나란히 놓고 볼 수 있다. 근거는 {@code docs/adr/018}.
 *
 * @param direction 방향. {@code plan.direction()} 과 같지만 읽는 쪽에서 자주 쓴다
 * @param plan 진입가 둘·손절·익절이 확정된 계획
 * @param context 진입 시점의 지표 판정과 근거
 */
public record TradeSignal(Direction direction, PositionPlan plan, MarketContext context) {

    public TradeSignal {
        DomainValues.required(direction, "진입 방향");
        DomainValues.required(plan, "진입 계획");
        DomainValues.required(context, "시장 맥락");
        if (direction != plan.direction()) {
            throw new InvalidBacktestException(
                    "신호의 방향과 계획의 방향이 다르다: %s / %s".formatted(direction, plan.direction()));
        }
    }
}
