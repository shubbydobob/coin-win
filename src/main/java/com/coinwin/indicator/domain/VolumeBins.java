package com.coinwin.indicator.domain;

import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.domain.Candle;
import com.coinwin.market.domain.CandleSeries;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

/**
 * 캔들을 가격 칸으로 흩뿌린다.
 *
 * <p><b>거래량을 고가와 저가 사이에 고르게 나눈다.</b> 봉 하나가 78,000~78,400 을 오갔다면 그
 * 거래량이 어느 가격에서 나왔는지 캔들만 봐서는 알 수 없다. 종가에 전부 몰면 임의의 한 점에
 * 없던 봉우리가 생기고, 4시간봉처럼 폭이 큰 봉에서 그 왜곡이 크다.
 *
 * <p><b>이것은 근사다.</b> 진짜 분포를 알려면 더 짧은 주기의 캔들이 필요하다 — 트레이딩뷰의
 * 거래량 프로파일도 하위 주기를 끌어다 쓴다. 여기서 그렇게 하지 않은 이유는 봉 300개짜리
 * 판독마다 하위 주기 수천 봉을 더 받아야 하기 때문이고, 봉이 많아지면 이 근사의 오차는
 * 평균으로 씻긴다. <b>알 수 없는 것을 아는 척하지 않되 아무 말도 하지 않는 쪽을 고르지도
 * 않았다.</b>
 *
 * <p>고가와 저가가 같은 봉은 나눌 것이 없어 그 가격이 든 칸에 통째로 들어간다.
 */
final class VolumeBins {

    /** 나누는 도중에는 정밀도를 넉넉히 둔다. {@link Quantity} 로 좁히는 것은 마지막에 한 번뿐이다. */
    private static final MathContext PRECISION = MathContext.DECIMAL64;

    private VolumeBins() {
    }

    static List<VolumeBin> over(CandleSeries series, int bins) {
        BigDecimal low = lowest(series);
        BigDecimal high = highest(series);
        BigDecimal width = high.subtract(low).divide(BigDecimal.valueOf(bins), PRECISION);
        BigDecimal[] volumes = new BigDecimal[bins];
        java.util.Arrays.fill(volumes, BigDecimal.ZERO);
        for (Candle candle : series.candles()) {
            spread(candle, volumes, low, width);
        }
        return assemble(volumes, low, width, high);
    }

    /**
     * 한 봉의 거래량을 겹치는 칸들에 나눠 넣는다.
     *
     * <p>칸의 경계에 정확히 걸린 값은 겹침 길이가 0 이라 어느 쪽에도 더해지지 않는다 — 그래서
     * 같은 거래량이 두 칸에 두 번 세어지는 일이 없다.
     */
    private static void spread(
            Candle candle, BigDecimal[] volumes, BigDecimal low, BigDecimal width) {
        BigDecimal bottom = candle.low().value();
        BigDecimal top = candle.high().value();
        BigDecimal volume = candle.volume().value();
        if (width.signum() == 0 || top.compareTo(bottom) == 0) {
            int bin = indexOf(bottom, low, width, volumes.length);
            volumes[bin] = volumes[bin].add(volume);
            return;
        }
        BigDecimal range = top.subtract(bottom);
        for (int bin = 0; bin < volumes.length; bin++) {
            BigDecimal binLow = low.add(width.multiply(BigDecimal.valueOf(bin)));
            BigDecimal overlap = overlap(bottom, top, binLow, width);
            if (overlap.signum() > 0) {
                volumes[bin] = volumes[bin].add(volume.multiply(overlap).divide(range, PRECISION));
            }
        }
    }

    private static BigDecimal overlap(
            BigDecimal bottom, BigDecimal top, BigDecimal binLow, BigDecimal width) {
        BigDecimal upper = top.min(binLow.add(width));
        BigDecimal lower = bottom.max(binLow);
        return upper.subtract(lower);
    }

    /** 칸 경계 위의 값은 위쪽 칸에 든다. 맨 위 칸만은 고가 자신을 품도록 닫혀 있다. */
    private static int indexOf(BigDecimal price, BigDecimal low, BigDecimal width, int bins) {
        if (width.signum() == 0) {
            return 0;
        }
        int index = price.subtract(low).divide(width, PRECISION).intValue();
        return Math.clamp(index, 0, bins - 1);
    }

    private static List<VolumeBin> assemble(
            BigDecimal[] volumes, BigDecimal low, BigDecimal width, BigDecimal high) {
        List<VolumeBin> bins = new ArrayList<>(volumes.length);
        for (int bin = 0; bin < volumes.length; bin++) {
            BigDecimal bottom = low.add(width.multiply(BigDecimal.valueOf(bin)));
            BigDecimal top = bin == volumes.length - 1 ? high : bottom.add(width);
            bins.add(new VolumeBin(
                    new PriceBand(Price.of(top), Price.of(bottom)), Quantity.of(volumes[bin])));
        }
        return List.copyOf(bins);
    }

    private static BigDecimal lowest(CandleSeries series) {
        return series.candles().stream()
                .map(candle -> candle.low().value())
                .min(BigDecimal::compareTo)
                .orElseThrow();
    }

    private static BigDecimal highest(CandleSeries series) {
        return series.candles().stream()
                .map(candle -> candle.high().value())
                .max(BigDecimal::compareTo)
                .orElseThrow();
    }
}
