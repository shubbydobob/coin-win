package com.coinwin.projection.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import java.util.List;

/**
 * 승패 순서 하나가 그리는 자산 곡선. 첫 점은 거래 이전의 초기 자본이다.
 *
 * <p>고정 비율 복리에서 <b>최종 자산은 승패의 순서를 기억하지 않는다.</b> 곱셈은 교환법칙을
 * 따르기 때문이다. 순서가 남기는 자국은 {@link #maxDrawdown()} 이다 — 같은 성적표라도 먼저
 * 지고 나중에 이긴 사람과 먼저 이기고 나중에 진 사람은 전혀 다른 계좌 화면을 본다.
 *
 * <p>그래서 이 도구는 최종 자산만 보여주지 않는다. 사람이 계획을 그만두게 만드는 것은
 * 최종 자산이 아니라 중간에 지나간 낙폭이다.
 */
public record EquityCurve(List<Money> points) {

    private static final Percentage NO_DRAWDOWN = Percentage.of("0");

    public EquityCurve {
        DomainValues.required(points, "자산 곡선의 점");
        if (points.isEmpty()) {
            throw new InvalidProjectionException("초기 자본조차 없는 자산 곡선은 성립하지 않는다");
        }
        points = List.copyOf(points);
    }

    public Money initialCapital() {
        return points.getFirst();
    }

    public Money finalEquity() {
        return points.getLast();
    }

    /** 첫 점은 거래 이전 상태이므로 거래 수는 점의 수보다 하나 적다. */
    public int trades() {
        return points.size() - 1;
    }

    /** 직전 고점 대비 가장 깊었던 하락폭. 순서 의존성이 드러나는 유일한 지표다. */
    public Percentage maxDrawdown() {
        Money peak = initialCapital();
        Percentage worst = NO_DRAWDOWN;
        for (Money point : points) {
            if (point.isGreaterThan(peak)) {
                peak = point;
                continue;
            }
            Percentage drop = peak.minus(point).percentOf(peak);
            if (drop.isGreaterThan(worst)) {
                worst = drop;
            }
        }
        return worst;
    }

    public boolean lostMoney() {
        return initialCapital().isGreaterThan(finalEquity());
    }
}
