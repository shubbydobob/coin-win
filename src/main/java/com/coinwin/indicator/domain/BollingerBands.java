package com.coinwin.indicator.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Price;
import com.coinwin.market.domain.Candle;
import com.coinwin.market.domain.CandleSeries;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.stream.IntStream;

/**
 * 볼린저 밴드 계산. 설정이 곧 계산기다 — 상태가 없는 순수 함수이므로 나눌 이유가 없다.
 *
 * <p><b>표준편차는 모집단 기준</b>(제곱합을 n 으로 나눈다)이다. 트레이딩뷰 {@code ta.stdev} 의
 * {@code biased} 기본값이 true, 즉 모집단 추정이기 때문이다. 표본 기준(n−1)으로 계산하면
 * 20봉에서 약 2.6% 넓은 밴드가 나오고 트레이딩뷰 값과 어긋난다.
 *
 * <p><b>상·하단은 스냅하지 않은 평균에서 계산한다.</b> 중심선을 스케일 2 로 반올림한 뒤 편차를
 * 더하면 이중 반올림으로 1센트가 갈린다. 평균이 60000.005 인 20봉에서 하단이 실제로 59999.96
 * 대신 59999.97 이 된다. 같은 원칙이 {@code projection} 의 자산 곡선에도 적용돼 있다.
 *
 * @param period 이동평균 구간. 트레이딩뷰 기본 20
 * @param multiplier 표준편차 배수. 트레이딩뷰 기본 2
 */
public record BollingerBands(int period, BigDecimal multiplier) {

    private static final String NAME = "볼린저 밴드";

    public BollingerBands {
        DomainValues.atLeast(period, 2, "볼린저 기간");
        DomainValues.required(multiplier, "표준편차 배수");
        if (multiplier.signum() <= 0) {
            throw new InvalidIndicatorException(
                    "표준편차 배수는 0 보다 커야 한다: " + multiplier.toPlainString());
        }
    }

    /** 트레이딩뷰 기본 설정 — 20봉 이동평균, 표준편차 2배. */
    public static BollingerBands standard() {
        return new BollingerBands(20, new BigDecimal("2"));
    }

    /**
     * 워밍업 구간을 뺀 모든 시점의 밴드. 앞 {@code period − 1} 봉은 평균이 성립하지 않아 빠진다.
     *
     * @throws InsufficientCandlesException 캔들이 {@code period} 개 미만인 경우
     */
    public List<IndicatorPoint<BollingerValue>> over(CandleSeries series) {
        DomainValues.required(series, "캔들 묶음");
        List<Candle> candles = series.candles();
        if (candles.size() < period) {
            throw new InsufficientCandlesException(NAME, period, candles.size());
        }
        return IntStream.range(period - 1, candles.size())
                .mapToObj(end -> new IndicatorPoint<>(
                        candles.get(end).openTime(), valueAt(candles, end)))
                .toList();
    }

    private BollingerValue valueAt(List<Candle> candles, int endIndex) {
        List<BigDecimal> closes = candles.subList(endIndex - period + 1, endIndex + 1).stream()
                .map(candle -> candle.close().value())
                .toList();
        BigDecimal mean = mean(closes);
        BigDecimal offset = standardDeviation(closes, mean).multiply(multiplier);
        return new BollingerValue(
                Price.of(mean.add(offset)), Price.of(mean), Price.of(mean.subtract(offset)));
    }

    private static BigDecimal mean(List<BigDecimal> values) {
        return values.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), MathContext.DECIMAL128);
    }

    private static BigDecimal standardDeviation(List<BigDecimal> values, BigDecimal mean) {
        BigDecimal sumOfSquares = values.stream()
                .map(value -> value.subtract(mean))
                .map(deviation -> deviation.multiply(deviation))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sumOfSquares
                .divide(BigDecimal.valueOf(values.size()), MathContext.DECIMAL128)
                .sqrt(MathContext.DECIMAL128);
    }
}
