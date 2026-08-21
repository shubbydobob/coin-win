package com.coinwin.position.domain;

import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;

/**
 * 분할 진입 중 {@code filledEntries} 건까지 체결됐을 때의 포지션 상태.
 *
 * <p>청산가와 최대손실을 계획이 아니라 <b>체결 상태</b>에 매단 이유가 있다. 분할 진입 계획에는
 * 단일한 청산가가 존재하지 않는다. 1차만 체결된 포지션과 전량 체결된 포지션은 평단이 다르고,
 * 따라서 청산가도 최대손실도 다른 값이다. 계획에 청산가 하나를 붙이면 그중 어느 쪽이 사라진다.
 */
public record FillState(
        int filledEntries,
        Price averageEntryPrice,
        Quantity quantity,
        Price liquidationPrice,
        Money maxLoss) {

    /**
     * 총수량 중 이 시점까지 채워진 몫으로 상태를 만든다.
     *
     * <p>{@code totalQuantity} 는 전량 체결을 전제로 손절가가 결정한 수량이다. 부분 체결
     * 수량은 그 총량에 체결된 비중을 적용해 얻는다 — 부분 체결 시점에 다시 사이징하지 않는다.
     *
     * <p>유지증거금 규칙을 <b>체결 상태마다 다시</b> 묻는다. 부분 체결과 전량 체결은 명목가가
     * 다르고, 명목가가 다르면 레버리지 구간이 다를 수 있기 때문이다. 계획 하나에 MMR 하나를
     * 물리면 그 차이가 사라진다.
     */
    static FillState of(PositionPlan plan, int filledEntries, Quantity totalQuantity,
            MaintenanceMarginPolicy policy) {
        Price average = plan.averageEntryPriceIfPartial(filledEntries);
        Quantity filled = plan.entries().allocationFilledAfter(filledEntries).applyTo(totalQuantity);
        PositionExposure exposure =
                new PositionExposure(plan.direction(), average, filled, plan.leverage());
        return new FillState(
                filledEntries,
                average,
                filled,
                exposure.liquidationPrice(policy.requirementFor(exposure.notional())),
                filled.times(average.absoluteDifference(plan.stopLoss())));
    }
}
