package com.coinwin.backtest.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Price;
import com.coinwin.market.domain.Candle;
import com.coinwin.market.domain.CandleSeries;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * 스윙 극값 탐지. 좌우 {@code lookback} 봉보다 <b>유일하게</b> 높은 고가가 스윙 고점이다.
 *
 * <p><b>동률은 피벗이 아니다.</b> 두 봉이 같은 극값이면 어느 쪽이 그 자리인지 정해지지 않고,
 * 그 모호함이 대의 개수를 데이터에 따라 흔든다. 둘 다 채택하면 같은 가격에 터치가 두 번
 * 찍혀 실제로는 한 번 반응한 자리가 최소 터치 기준을 통과한다.
 *
 * <p>결과의 {@code confirmedAt} 은 {@code i + lookback} 봉의 시각이다. 이 값이 백테스트의
 * 룩어헤드를 막는 유일한 장치다 — {@code at} 만 들고 있으면 시점 {@code t} 에서 아직 확정되지
 * 않은 극값이 섞여 들어온다.
 *
 * @param lookback 좌우로 비교할 봉 수
 */
public record PivotDetector(int lookback) {

    public PivotDetector {
        DomainValues.atLeast(lookback, 1, "피벗 탐지 폭");
    }

    /** 시간 오름차순으로 정렬된 피벗. 같은 봉이 고점이면서 저점일 수는 없다. */
    public List<Pivot> over(CandleSeries series) {
        DomainValues.required(series, "캔들 묶음");
        List<Candle> candles = series.candles();
        List<Pivot> pivots = new ArrayList<>();
        IntStream.range(lookback, candles.size() - lookback)
                .forEach(i -> addPivotAt(candles, i, pivots));
        return List.copyOf(pivots);
    }

    private void addPivotAt(List<Candle> candles, int index, List<Pivot> pivots) {
        if (isExtreme(candles, index, Candle::high, Price::isAbove)) {
            pivots.add(pivotAt(candles, index, candles.get(index).high(), PivotKind.SWING_HIGH));
        } else if (isExtreme(candles, index, Candle::low, Price::isBelow)) {
            pivots.add(pivotAt(candles, index, candles.get(index).low(), PivotKind.SWING_LOW));
        }
    }

    private Pivot pivotAt(List<Candle> candles, int index, Price price, PivotKind kind) {
        return new Pivot(candles.get(index).openTime(),
                candles.get(index + lookback).openTime(), price, kind);
    }

    /**
     * 좌우 {@code lookback} 봉 전부보다 엄격하게 크거나(고점) 작은가(저점).
     *
     * <p>{@code isAbove} / {@code isBelow} 는 스케일이 아니라 값으로 비교하므로 {@code 100.0} 과
     * {@code 100.00} 이 동률로 잡힌다. 동률을 극값으로 보지 않는 규칙이 여기에 걸린다.
     */
    private boolean isExtreme(List<Candle> candles, int index,
            Function<Candle, Price> extract, BiPredicate<Price, Price> beats) {
        Price subject = extract.apply(candles.get(index));
        return IntStream.rangeClosed(index - lookback, index + lookback)
                .filter(i -> i != index)
                .mapToObj(i -> extract.apply(candles.get(i)))
                .allMatch(other -> beats.test(subject, other));
    }
}
