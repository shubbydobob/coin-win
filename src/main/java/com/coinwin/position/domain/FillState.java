package com.coinwin.position.domain;

import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import java.math.BigDecimal;

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
     */
    static FillState of(PositionPlan plan, int filledEntries, Quantity totalQuantity, Percentage mmr) {
        Price average = plan.averageEntryPriceIfPartial(filledEntries);
        Quantity filled = plan.entries().allocationFilledAfter(filledEntries).applyTo(totalQuantity);
        return new FillState(
                filledEntries,
                average,
                filled,
                average.multipliedBy(liquidationFactor(plan, mmr)),
                filled.times(average.absoluteDifference(plan.stopLoss())));
    }

    /**
     * {@code LONG: 1 - 1/leverage + MMR}, {@code SHORT: 1 + 1/leverage - MMR}.
     *
     * <p>평단에 곱하면 청산가가 된다. MMR 은 주입받는다 — 고정 근사치를 여기에 박아 두면
     * Phase 3 에서 구간별 MMR 로 바뀔 때 공식과 입력 중 무엇이 틀렸는지 구분할 수 없다.
     */
    private static BigDecimal liquidationFactor(PositionPlan plan, Percentage mmr) {
        BigDecimal inverseLeverage = plan.inverseLeverage();
        return switch (plan.direction()) {
            case LONG -> BigDecimal.ONE.subtract(inverseLeverage).add(mmr.asFraction());
            case SHORT -> BigDecimal.ONE.add(inverseLeverage).subtract(mmr.asFraction());
        };
    }
}
