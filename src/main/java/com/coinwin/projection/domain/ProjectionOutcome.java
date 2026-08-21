package com.coinwin.projection.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;

/**
 * 시행 한 번이 남기는 것 — 어디에 도착했는가, 도중에 얼마나 깊이 빠졌는가.
 *
 * <p>곡선 전체를 들고 있지 않는다. 분포를 내는 데 필요한 것은 이 둘뿐이고, 시행 수천 번의
 * 점을 전부 붙들면 메모리만 먹는다. 곡선을 보고 싶으면 {@link ProjectionSpec#simulate(long)}
 * 로 그 시드 하나를 다시 그리면 된다 — 결정론이라 언제 다시 그려도 같은 곡선이다.
 */
public record ProjectionOutcome(Money finalEquity, Percentage maxDrawdown) {

    public static ProjectionOutcome of(EquityCurve curve) {
        DomainValues.required(curve, "자산 곡선");
        return new ProjectionOutcome(curve.finalEquity(), curve.maxDrawdown());
    }
}
