package com.coinwin.indicator.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import com.coinwin.market.domain.Candle;
import com.coinwin.market.domain.CandleSeries;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * 실제 변동폭(Average True Range). 설정이 곧 계산기다 — 볼린저·일목과 같은 형태다.
 *
 * <p>Phase 6 백테스트가 <b>대의 군집 허용치·폭·손절 버퍼를 전부 ATR 배수로</b> 표현하기 때문에
 * 들어왔다. 15분봉과 일봉에 같은 고정 백분율을 쓰면 한쪽은 대가 터무니없이 넓거나 좁아지는데,
 * ATR 배수는 주기마다 재튜닝할 필요가 없다.
 *
 * <p><b>반환이 {@link Money} 인 이유</b>는 ATR 이 가격이 아니라 <b>가격 거리</b>이기 때문이다.
 * {@link Price#absoluteDifference} 도 {@link PriceBand#width()} 도 이미 같은 뜻에 같은 타입을
 * 쓴다.
 *
 * <p>공식은 트레이딩뷰가 배포하는 Pine 소스 원문과 대조해 확정했다 — {@code docs/adr/018}.
 * {@code plot(ma_function(ta.tr(true), length))} 이고 평활 기본값이 {@code RMA} 다.
 *
 * @param period 평활 구간. 트레이딩뷰 기본 14
 */
public record AverageTrueRange(int period) {

    private static final String NAME = "ATR";

    public AverageTrueRange {
        DomainValues.atLeast(period, 1, "ATR 기간");
    }

    /** 트레이딩뷰 기본 설정 — 14봉 Wilder 평활. */
    public static AverageTrueRange standard() {
        return new AverageTrueRange(14);
    }

    /**
     * 워밍업 구간을 뺀 모든 시점의 ATR.
     *
     * <p>첫 값은 {@code period} 번째가 아니라 <b>{@code period − 1} 인덱스</b>에 나온다.
     * {@code ta.tr(true)} 가 첫 봉에도 값을 내기 때문이다 — 직전 종가가 없으면 고가−저가로
     * 대신한다. 첫 봉을 버리면 모든 값이 한 칸 밀리고, 그래도 그럴듯한 숫자가 나온다.
     *
     * @throws InsufficientCandlesException 캔들이 {@code period} 개 미만인 경우
     */
    public List<IndicatorPoint<Money>> over(CandleSeries series) {
        DomainValues.required(series, "캔들 묶음");
        List<Candle> candles = series.candles();
        if (candles.size() < period) {
            throw new InsufficientCandlesException(NAME, period, candles.size());
        }
        List<BigDecimal> smoothed = smooth(trueRanges(candles));
        return IntStream.range(0, smoothed.size())
                .mapToObj(i -> new IndicatorPoint<>(
                        candles.get(i + period - 1).openTime(), Money.of(smoothed.get(i))))
                .toList();
    }

    private static List<BigDecimal> trueRanges(List<Candle> candles) {
        return IntStream.range(0, candles.size())
                .mapToObj(i -> trueRange(candles.get(i), i == 0 ? null : candles.get(i - 1)))
                .toList();
    }

    /**
     * {@code max(고가−저가, |고가−직전종가|, |저가−직전종가|)}.
     *
     * <p>뒤의 둘이 갭을 잡는다. 고저폭만 보면 직전 종가에서 뛰어 열린 봉의 실제 위험이 통째로
     * 사라지고, ATR 에 매달린 손절 버퍼가 실제보다 좁아진다.
     */
    private static BigDecimal trueRange(Candle candle, Candle previous) {
        BigDecimal highLow = candle.high().absoluteDifference(candle.low()).value();
        if (previous == null) {
            return highLow;
        }
        Price close = previous.close();
        return highLow
                .max(candle.high().absoluteDifference(close).value())
                .max(candle.low().absoluteDifference(close).value());
    }

    /**
     * Wilder 평활. 시드는 첫 {@code period} 개의 단순평균이고, 이후는
     * {@code (직전 × (n−1) + TR) / n} 이다. 트레이딩뷰 {@code ta.rma} 와 같다.
     *
     * <p><b>점화식은 반올림하지 않은 값으로 이어 간다.</b> 단계마다 스케일 2 로 스냅하면 오차가
     * 누적되고, 그 오차는 ATR 배수로 정의된 대 폭과 손절 버퍼로 그대로 번진다. 같은 원칙이
     * 볼린저 상하단({@code docs/adr/015})과 자산 곡선({@code docs/adr/010})에도 적용돼 있다.
     */
    private List<BigDecimal> smooth(List<BigDecimal> trueRanges) {
        BigDecimal divisor = BigDecimal.valueOf(period);
        BigDecimal current = trueRanges.subList(0, period).stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(divisor, MathContext.DECIMAL128);
        List<BigDecimal> smoothed = new ArrayList<>();
        smoothed.add(current);
        for (BigDecimal trueRange : trueRanges.subList(period, trueRanges.size())) {
            current = current.multiply(BigDecimal.valueOf(period - 1L))
                    .add(trueRange)
                    .divide(divisor, MathContext.DECIMAL128);
            smoothed.add(current);
        }
        return smoothed;
    }
}
