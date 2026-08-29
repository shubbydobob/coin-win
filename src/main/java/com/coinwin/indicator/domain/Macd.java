package com.coinwin.indicator.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.market.domain.Candle;
import com.coinwin.market.domain.CandleSeries;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * MACD. 빠른 이동평균과 느린 이동평균이 벌어지는 정도다.
 *
 * <p><b>정의는 트레이딩뷰 원문에서 확정했다</b>({@code STD;MACD/18.0}):
 *
 * <pre>{@code
 * fast_ma = sma_source ? sma(src, fast_length) : ema(src, fast_length)
 * slow_ma = sma_source ? sma(src, slow_length) : ema(src, slow_length)
 * macd    = fast_ma - slow_ma
 * signal  = sma_signal ? sma(macd, signal_length) : ema(macd, signal_length)
 * hist    = macd - signal
 * }</pre>
 *
 * <p>두 {@code sma_*} 스위치의 기본값이 모두 {@code false} 이므로 <b>기본은 EMA 넷</b>이다.
 * 여기서는 그 기본만 구현한다 — 쓰지 않을 스위치를 미리 만들지 않는다.
 *
 * <p><b>두 평균의 자리를 맞추는 것이 이 구현의 유일한 까다로운 부분이다.</b> 빠른 EMA 는
 * {@code fast − 1} 번째 봉부터, 느린 EMA 는 {@code slow − 1} 번째 봉부터 값을 갖는다. 그대로
 * 빼면 <b>서로 다른 봉의 값을 빼게 되고</b>, 그 오차는 그럴듯한 곡선으로 나온다. 봉 번호로
 * 맞춘 뒤 뺀다.
 *
 * @param fast 빠른 EMA 구간. 트레이딩뷰 기본 12
 * @param slow 느린 EMA 구간. 트레이딩뷰 기본 26
 * @param signalLength 시그널 EMA 구간. 트레이딩뷰 기본 9
 */
public record Macd(int fast, int slow, int signalLength) {

    private static final String NAME = "MACD";

    public Macd {
        DomainValues.atLeast(fast, 1, "MACD 빠른 구간");
        DomainValues.atLeast(slow, 2, "MACD 느린 구간");
        DomainValues.atLeast(signalLength, 1, "MACD 시그널 구간");
        if (fast >= slow) {
            throw new InvalidIndicatorException(
                    "빠른 구간은 느린 구간보다 짧아야 한다: %d >= %d".formatted(fast, slow));
        }
    }

    /** 트레이딩뷰 기본 설정 — 12 / 26 / 9. */
    public static Macd standard() {
        return new Macd(12, 26, 9);
    }

    /** 시그널까지 나오려면 봉이 몇 개 필요한가. */
    public int warmup() {
        return slow + signalLength - 1;
    }

    /**
     * 워밍업을 뺀 모든 시점의 MACD.
     *
     * @throws InsufficientCandlesException 봉이 {@link #warmup()} 개 미만인 경우
     */
    public List<IndicatorPoint<MacdValue>> over(CandleSeries series) {
        DomainValues.required(series, "캔들 묶음");
        List<Candle> candles = series.candles();
        if (candles.size() < warmup()) {
            throw new InsufficientCandlesException(NAME, warmup(), candles.size());
        }

        List<BigDecimal> macdLine = macdLine(candles);
        List<BigDecimal> signal = Smoothing.ema(macdLine, signalLength);
        List<IndicatorPoint<MacdValue>> out = new ArrayList<>(signal.size());
        for (int i = 0; i < signal.size(); i++) {
            int macdIndex = i + signalLength - 1;
            Candle at = candles.get(macdIndex + slow - 1);
            out.add(new IndicatorPoint<>(
                    at.openTime(), MacdValue.of(macdLine.get(macdIndex), signal.get(i))));
        }
        return out;
    }

    /**
     * 두 EMA 의 차. <b>봉 번호로 맞춘 뒤 뺀다.</b>
     *
     * <p>빠른 EMA 의 {@code j} 번째 값은 봉 {@code j + fast − 1} 의 것이고, 느린 쪽은
     * {@code i + slow − 1} 이다. 인덱스를 그대로 맞대면 서로 다른 봉을 빼게 되고 그 오차는
     * 그럴듯한 곡선으로 나온다 — 값을 눈으로 봐서는 알 수 없는 종류다.
     */
    private List<BigDecimal> macdLine(List<Candle> candles) {
        List<BigDecimal> closes = candles.stream().map(candle -> candle.close().value()).toList();
        List<BigDecimal> fastEma = Smoothing.ema(closes, fast);
        List<BigDecimal> slowEma = Smoothing.ema(closes, slow);

        List<BigDecimal> out = new ArrayList<>(slowEma.size());
        for (int i = 0; i < slowEma.size(); i++) {
            int bar = i + slow - 1;
            out.add(fastEma.get(bar - (fast - 1)).subtract(slowEma.get(i)));
        }
        return out;
    }
}
