package com.coinwin.indicator.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Price;
import com.coinwin.market.domain.Candle;
import com.coinwin.market.domain.CandleSeries;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 이동평균선. 단순(SMA)과 지수(EMA) 둘.
 *
 * <p><b>둘을 한 타입에 둔 이유는 쓰는 쪽이 고르기 때문이다.</b> 화면에 20·60·120 을 함께
 * 놓을 때 어느 것이 단순이고 어느 것이 지수인지가 설정이지 종류가 아니다. 계산 자체는
 * {@link Smoothing} 이 갖고 여기서는 고르고 값 객체로 옮기기만 한다.
 *
 * <p><b>결과가 {@code Price} 다.</b> 평균 가격은 가격이므로 스케일 2 로 반올림된다 —
 * 볼린저 중심선과 같은 취급이다.
 *
 * @param period 구간
 * @param type 단순인가 지수인가
 */
public record MovingAverage(int period, MovingAverage.Type type) {

    private static final String NAME = "이동평균";

    /** 어떻게 평활하는가. */
    public enum Type {
        /** 산술평균. */
        SIMPLE,
        /** 지수 가중, {@code alpha = 2 / (n + 1)}. */
        EXPONENTIAL
    }

    public MovingAverage {
        DomainValues.atLeast(period, 1, "이동평균 기간");
        DomainValues.required(type, "이동평균 종류");
    }

    public static MovingAverage simple(int period) {
        return new MovingAverage(period, Type.SIMPLE);
    }

    public static MovingAverage exponential(int period) {
        return new MovingAverage(period, Type.EXPONENTIAL);
    }

    /**
     * 워밍업을 뺀 모든 시점의 이동평균.
     *
     * @throws InsufficientCandlesException 봉이 {@code period} 개 미만인 경우
     */
    public List<IndicatorPoint<Price>> over(CandleSeries series) {
        DomainValues.required(series, "캔들 묶음");
        List<Candle> candles = series.candles();
        if (candles.size() < period) {
            throw new InsufficientCandlesException(NAME, period, candles.size());
        }

        List<BigDecimal> closes = candles.stream().map(candle -> candle.close().value()).toList();
        List<BigDecimal> smoothed =
                type == Type.SIMPLE ? Smoothing.sma(closes, period) : Smoothing.ema(closes, period);

        List<IndicatorPoint<Price>> out = new ArrayList<>(smoothed.size());
        for (int i = 0; i < smoothed.size(); i++) {
            out.add(new IndicatorPoint<>(
                    candles.get(i + period - 1).openTime(), Price.of(smoothed.get(i))));
        }
        return out;
    }
}
