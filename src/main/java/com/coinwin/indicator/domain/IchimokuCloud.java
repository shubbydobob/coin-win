package com.coinwin.indicator.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Price;
import com.coinwin.market.domain.Candle;
import com.coinwin.market.domain.CandleSeries;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * 일목균형표 계산. 설정이 곧 계산기다 — 상태가 없는 순수 함수이므로 나눌 이유가 없다.
 *
 * <p>세 선은 모두 <b>같은 형태</b>다: 구간의 최고가와 최저가의 중간값. 기간만 다르다.
 * 선행스팬 1 만 예외로 전환선과 기준선의 평균이다.
 *
 * <p><b>변위 26 은 실제로 25봉을 민다.</b> 트레이딩뷰 내장 지표의 Pine 소스가 그렇게 돼 있다.
 *
 * <pre>{@code
 * displacement = input.int(26, minval=1, title="Lagging Span")
 * p1 = plot(leadLine1, offset = displacement - 1, ...)
 * plot(close, offset = -displacement + 1, title="Lagging Span")
 * }</pre>
 *
 * <p>입력이 26 인데 미는 것은 25 다. 현재 봉을 1 번째로 세기 때문이다. 그래서 이 record 도
 * <b>입력 어휘를 트레이딩뷰와 맞추고</b>({@code displacement = 26}) 실제 이동은
 * {@link #shift()} 가 {@code displacement − 1} 로 계산한다. 26 을 25 로 바꿔 저장하면
 * 차트에서 26 을 본 사람이 코드에서 25 를 보게 되고, 그 불일치가 매번 다시 검증된다.
 *
 * <p>근거는 {@code docs/adr/014} — 트레이딩뷰가 배포하는 Pine 소스 원문을 인용해 두었다.
 *
 * @param conversionPeriod 전환선 기간. 트레이딩뷰 기본 9
 * @param basePeriod 기준선 기간. 기본 26
 * @param leadingSpanBPeriod 선행스팬 2 기간. 기본 52
 * @param displacement 변위 입력값. 기본 26 이고 실제 이동은 25 다 ({@link #shift()})
 */
public record IchimokuCloud(
        int conversionPeriod, int basePeriod, int leadingSpanBPeriod, int displacement) {

    private static final String NAME = "일목균형표";

    public IchimokuCloud {
        DomainValues.atLeast(conversionPeriod, 1, "전환선 기간");
        DomainValues.atLeast(basePeriod, 1, "기준선 기간");
        DomainValues.atLeast(leadingSpanBPeriod, 1, "선행스팬 2 기간");
        DomainValues.atLeast(displacement, 1, "변위");
        assertPeriodsAreOrdered(conversionPeriod, basePeriod, leadingSpanBPeriod);
    }

    /** 트레이딩뷰 기본 설정 — 9 / 26 / 52, 변위 26. */
    public static IchimokuCloud standard() {
        return new IchimokuCloud(9, 26, 52, 26);
    }

    /**
     * 실제로 미는 봉 수. 트레이딩뷰가 {@code offset = displacement − 1} 을 쓰므로 기본 25 다.
     *
     * <p>현재 봉을 1 번째로 세는 관습에서 나온 값이다. 여기서 한 봉이 어긋나면 구름 전체가
     * 한 칸 밀리고, 그 상태로도 모든 값이 그럴듯해 보인다.
     */
    private int shift() {
        return displacement - 1;
    }

    /**
     * 워밍업 구간을 뺀 모든 시점의 다섯 선.
     *
     * <p>첫 값은 {@code shift + 선행스팬 2 기간 − 1} 번째 캔들에서 나온다. 그 시점의 구름이
     * {@code shift} 만큼 앞선 위치에서 계산되고, 그 계산에 다시 선행스팬 2 기간만큼의 캔들이
     * 필요하기 때문이다. 기본 설정에서 25 + 52 = 77 개다.
     *
     * @throws InsufficientCandlesException 워밍업 구간을 채우지 못하는 경우
     */
    public List<IndicatorPoint<IchimokuValue>> over(CandleSeries series) {
        DomainValues.required(series, "캔들 묶음");
        List<Candle> candles = series.candles();
        int firstIndex = shift() + leadingSpanBPeriod - 1;
        if (candles.size() <= firstIndex) {
            throw new InsufficientCandlesException(NAME, firstIndex + 1, candles.size());
        }
        return IntStream.range(firstIndex, candles.size())
                .mapToObj(index -> new IndicatorPoint<>(
                        candles.get(index).openTime(), valueAt(candles, index)))
                .toList();
    }

    private IchimokuValue valueAt(List<Candle> candles, int index) {
        int cloudSource = index - shift();
        return new IchimokuValue(
                midpoint(candles, index, conversionPeriod),
                midpoint(candles, index, basePeriod),
                average(midpoint(candles, cloudSource, conversionPeriod).value(),
                        midpoint(candles, cloudSource, basePeriod).value()),
                midpoint(candles, cloudSource, leadingSpanBPeriod),
                laggingSpanAt(candles, index));
    }

    /** 구간 {@code (endIndex − period, endIndex]} 의 최고가와 최저가의 중간값. */
    private static Price midpoint(List<Candle> candles, int endIndex, int period) {
        List<Candle> window = candles.subList(endIndex - period + 1, endIndex + 1);
        BigDecimal high = window.stream()
                .map(candle -> candle.high().value()).max(BigDecimal::compareTo).orElseThrow();
        BigDecimal low = window.stream()
                .map(candle -> candle.low().value()).min(BigDecimal::compareTo).orElseThrow();
        return average(high, low);
    }

    private static Price average(BigDecimal one, BigDecimal other) {
        return Price.of(one.add(other).divide(BigDecimal.TWO, MathContext.DECIMAL128));
    }

    /**
     * {@code shift} 만큼 뒤의 종가. 최근 {@code shift} 봉에는 아직 존재하지 않는다.
     *
     * <p>트레이딩뷰의 {@code plot(close, offset = -displacement + 1)} 과 같은 이동이다.
     * 선행스팬과 <b>대칭</b>이라 같은 {@link #shift()} 를 쓴다.
     */
    private Optional<Price> laggingSpanAt(List<Candle> candles, int index) {
        int target = index + shift();
        return target < candles.size()
                ? Optional.of(candles.get(target).close())
                : Optional.empty();
    }

    /**
     * 전환선 ≤ 기준선 ≤ 선행스팬 2 여야 한다.
     *
     * <p>순서가 뒤집히면 "빠른 선이 느린 선을 교차한다" 는 해석 자체가 성립하지 않는다.
     * 9/26/52 든 20/60/120 이든 실제로 쓰이는 조합은 전부 이 순서다.
     */
    private static void assertPeriodsAreOrdered(int conversion, int base, int leadingSpanB) {
        if (conversion > base || base > leadingSpanB) {
            throw new InvalidIndicatorException(
                    "기간은 전환선 ≤ 기준선 ≤ 선행스팬 2 순서여야 한다: %d / %d / %d"
                            .formatted(conversion, base, leadingSpanB));
        }
    }
}
