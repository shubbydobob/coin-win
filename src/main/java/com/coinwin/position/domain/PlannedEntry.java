package com.coinwin.position.domain;

import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;

/**
 * 분할 진입 한 건. 가격과 그 가격에 배정한 비중이다.
 *
 * <p>비중은 수량이 아니라 <b>비율</b>이다. 총수량은 손절가가 결정하므로, 계획 시점에는
 * "얼마나 살지" 가 아니라 "어느 가격에 몇 퍼센트를 나눠 둘지" 만 정해진다.
 */
public record PlannedEntry(Price price, Percentage allocation) {

    public PlannedEntry {
        PositionValues.required(price, "진입가");
        PositionValues.required(allocation, "분할 비중");
        if (allocation.value().signum() == 0) {
            throw new InvalidPositionPlanException("분할 비중이 0% 인 진입 계획은 의미가 없다");
        }
    }

    public static PlannedEntry of(String price, String allocation) {
        return new PlannedEntry(Price.of(price), Percentage.of(allocation));
    }
}
