package com.coinwin.readout.domain;

import com.coinwin.common.domain.Price;
import com.coinwin.indicator.domain.IndicatorPoint;
import com.coinwin.market.domain.Candle;
import com.coinwin.market.domain.CandleSeries;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.function.IntFunction;

/**
 * 그 봉의 값을 찾는다.
 *
 * <p><b>인덱스가 아니라 시각으로 찾는다</b> — 지표마다 시작하는 봉이 달라 같은 인덱스가 같은
 * 시각을 뜻하지 않는다.
 *
 * <p><b>이진 탐색인 이유는 과거를 훑는 쪽 때문이다.</b> 화면은 마지막 봉 하나만 물으므로
 * 선형이어도 티가 안 나지만, 1만5천 봉을 훑으며 봉마다 다섯 지표를 물으면 그것이
 * 1만5천 × 5 × 1만5천 이 된다. 점들은 시간순이므로 반씩 접을 수 있다.
 *
 * <p><b>판정에서 떼어 낸 이유는 같은 탐색이 두 번 나왔기 때문이다.</b> 지표 점과 캔들이 각각
 * 자기 이진 탐색을 갖고 있었고, 그중 한쪽만 고치면 판정이 두 시각을 섞어 읽게 된다.
 */
final class BarLookup {

    private BarLookup() {
    }

    /** 그 봉의 지표 값. 점이 없거나 그 봉에 값이 없으면 {@code null}. */
    static <T> T valueAt(List<IndicatorPoint<T>> points, Instant bar) {
        if (points == null || points.isEmpty()) {
            return null;
        }
        int index = indexOf(points.size(), i -> points.get(i).at(), bar);
        return index < 0 ? null : points.get(index).value();
    }

    /** 그 봉의 종가. 그 시각에 봉이 없으면 {@code null}. */
    static BigDecimal closeAt(CandleSeries candles, Instant bar) {
        List<Candle> bars = candles.candles();
        int index = indexOf(bars.size(), i -> bars.get(i).openTime(), bar);
        return index < 0 ? null : bars.get(index).close().value();
    }

    /**
     * 그 구간 이동평균선의 그 봉 값.
     *
     * <p><b>구간을 이름으로 고른다.</b> 목록의 자리로 고르면 구간을 하나 더하는 날 판정의
     * 뜻이 조용히 바뀐다 — 실제로 셋에서 다섯으로 늘리면서 그 자리가 생겼다.
     */
    static BigDecimal movingAverageAt(List<MovingAverageLine> averages, int period, Instant bar) {
        return averages.stream()
                .filter(line -> line.period() == period)
                .findFirst()
                .map(line -> valueAt(line.points(), bar))
                .map(Price::value)
                .orElse(null);
    }

    /** 시간순 목록에서 그 시각의 자리. 없으면 −1. */
    private static int indexOf(int size, IntFunction<Instant> at, Instant bar) {
        int low = 0;
        int high = size - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int cmp = at.apply(mid).compareTo(bar);
            if (cmp == 0) {
                return mid;
            }
            if (cmp < 0) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return -1;
    }
}
