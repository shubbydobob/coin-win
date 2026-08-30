package com.coinwin.readout.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import com.coinwin.indicator.domain.BollingerValue;
import com.coinwin.indicator.domain.IndicatorPoint;
import com.coinwin.indicator.domain.MacdValue;
import com.coinwin.market.domain.CandleSeries;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * <b>한 봉만 봐서는 나오지 않는 값들</b>을 봉마다 낸다.
 *
 * <p><b>목록으로 내는 것이 이 타입의 요점이다.</b> 화면은 마지막 값 하나만 쓰지만, "이 자리에
 * 섰을 때 다음에 무슨 일이 있었나" 를 재려면 <b>과거의 모든 봉</b>에서 같은 값이 나와야 한다.
 * 마지막 봉 전용으로 짜 두면 재는 쪽이 같은 규칙을 다시 쓰게 되고, 그 순간 화면이 보여 주는
 * 값과 측정이 판정한 값이 갈라진다 — {@code docs/spec/indicator-usage.md} § 5.2 가 파이썬에
 * 대해 경고한 것과 같은 위험이 자바 안에서도 성립한다.
 *
 * <p><b>한 번에 훑는다.</b> 봉마다 창을 다시 여는 방식이면 6년 21만 봉에서 그 자체가 돌지
 * 않는다 — 지표 계산기들이 전 구간을 한 번 훑어 목록을 내는 것과 같은 모양을 지킨다.
 */
public final class IndicatorDerivations {

    /**
     * 밴드폭 순위를 재는 창.
     *
     * <p><b>판독이 보는 봉 수와 같은 수다</b>({@code ReadoutService.BARS}). 순위를 위해 더 긴
     * 창을 따로 쓰면 같은 줄에 놓인 수들이 서로 다른 과거를 가리킨다. 화면에서는 목록이
     * 이 수보다 짧아 사실상 전 구간이고, 6년을 훑는 측정에서만 창이 실제로 걸린다.
     */
    public static final int RANK_WINDOW = 300;

    private IndicatorDerivations() {
    }

    /**
     * 봉마다 <b>그 시점까지의 창 안에서</b> 밴드폭이 몇 번째인가.
     *
     * <p>같거나 좁은 봉의 비율이므로 100 이면 그 창에서 가장 넓은 것이고 작을수록 수축이다.
     * <b>뒤를 보지 않는다</b> — 창은 언제나 그 봉에서 끝난다.
     */
    public static List<Percentage> bandWidthRanks(List<IndicatorPoint<BollingerValue>> points) {
        DomainValues.required(points, "밴드 점");
        List<Percentage> ranks = new ArrayList<>(points.size());
        for (int i = 0; i < points.size(); i++) {
            BigDecimal now = points.get(i).value().bandWidth().value();
            int from = Math.max(0, i - RANK_WINDOW + 1);
            long atOrBelow = 0;
            for (int j = from; j <= i; j++) {
                if (points.get(j).value().bandWidth().value().compareTo(now) <= 0) {
                    atOrBelow++;
                }
            }
            ranks.add(Percentage.ofRatio(atOrBelow, i - from + 1));
        }
        return List.copyOf(ranks);
    }

    /**
     * 봉마다 <b>밴드 밖에 연속으로 머문 봉 수.</b> 양수면 상단 밖, 음수면 하단 밖이고 0 이면
     * 그 봉에서 안에 있다.
     *
     * <p><b>부호를 그대로 실어 낸다</b> — 방향과 길이를 따로 두면 부르는 쪽이 둘을 맞대는
     * 규칙을 또 갖게 된다.
     */
    public static List<Integer> bandWalks(
            List<IndicatorPoint<BollingerValue>> points, CandleSeries series) {
        DomainValues.required(points, "밴드 점");
        DomainValues.required(series, "캔들 묶음");
        List<Integer> walks = new ArrayList<>(points.size());
        int run = 0;
        int side = 0;
        for (IndicatorPoint<BollingerValue> point : points) {
            int now = sideOf(point, series);
            run = now == side ? run + 1 : 1;
            side = now;
            walks.add(side == 0 ? 0 : side * run);
        }
        return List.copyOf(walks);
    }

    /**
     * 봉마다 <b>지금 히스토그램 부호가 이어진 봉 수.</b> 양수면 시그널 위, 음수면 아래이고
     * 0 이면 히스토그램이 정확히 0 이다.
     */
    public static List<Integer> macdRuns(List<IndicatorPoint<MacdValue>> points) {
        DomainValues.required(points, "MACD 점");
        List<Integer> runs = new ArrayList<>(points.size());
        int run = 0;
        int side = 0;
        for (IndicatorPoint<MacdValue> point : points) {
            int now = point.value().histogram().signum();
            run = now == side ? run + 1 : 1;
            side = now;
            runs.add(side == 0 ? 0 : side * run);
        }
        return List.copyOf(runs);
    }

    /** 그 봉의 종가가 밴드 밖 어느 쪽인가. 안이거나 봉을 못 찾으면 0 이다. */
    private static int sideOf(IndicatorPoint<BollingerValue> point, CandleSeries series) {
        BigDecimal close = BarLookup.closeAt(series, point.at());
        if (close == null) {
            return 0;
        }
        return switch (point.value().positionOf(Price.of(close))) {
            case ABOVE -> 1;
            case BELOW -> -1;
            case INSIDE -> 0;
        };
    }
}
