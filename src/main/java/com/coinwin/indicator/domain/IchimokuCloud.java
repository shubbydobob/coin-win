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
 * <p><b>변위를 상수로 박지 않고 설정으로 두는 이유.</b> 바이낸스·트레이딩뷰의 일목은 구름을
 * 26봉 앞에 그린다고 문서화돼 있지만, 트레이딩뷰 내장 지표의 플롯 오프셋이 현재 봉을 1 로 세어
 * {@code displacement − 1} 즉 25 라는 설명도 널리 퍼져 있다. 차트에 그리는 위치의 문제이면서
 * 동시에 "지금 봉의 구름이 몇 봉 전 계산값인가" 를 정하므로 값에 영향을 준다. 공개 문서만으로
 * 확정되지 않으므로 기본값 26 으로 두고, 실제 차트와 대조해 어긋나면 숫자 하나만 바꾼다.
 *
 * <p>{@code archfixture} 가 아니라 여기 적어 두는 이유는, 이것이 규칙이 아니라 <b>아직 대조하지
 * 못한 전제</b>이기 때문이다. 대조 결과는 {@code docs/adr/014} 에 기록한다.
 *
 * @param conversionPeriod 전환선 기간. 트레이딩뷰 기본 9
 * @param basePeriod 기준선 기간. 기본 26
 * @param leadingSpanBPeriod 선행스팬 2 기간. 기본 52
 * @param displacement 변위. 선행스팬은 이만큼 앞, 후행스팬은 이만큼 뒤. 기본 26
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
     * 워밍업 구간을 뺀 모든 시점의 다섯 선.
     *
     * <p>첫 값은 {@code 변위 + 선행스팬 2 기간 − 1} 번째 캔들에서 나온다. 그 시점의 구름이
     * 변위만큼 앞선 위치에서 계산되고, 그 계산에 다시 선행스팬 2 기간만큼의 캔들이 필요하기
     * 때문이다. 기본 설정에서 26 + 52 = 78 개다.
     *
     * @throws InsufficientCandlesException 워밍업 구간을 채우지 못하는 경우
     */
    public List<IndicatorPoint<IchimokuValue>> over(CandleSeries series) {
        DomainValues.required(series, "캔들 묶음");
        List<Candle> candles = series.candles();
        int firstIndex = displacement + leadingSpanBPeriod - 1;
        if (candles.size() <= firstIndex) {
            throw new InsufficientCandlesException(NAME, firstIndex + 1, candles.size());
        }
        return IntStream.range(firstIndex, candles.size())
                .mapToObj(index -> new IndicatorPoint<>(
                        candles.get(index).openTime(), valueAt(candles, index)))
                .toList();
    }

    private IchimokuValue valueAt(List<Candle> candles, int index) {
        int cloudSource = index - displacement;
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

    /** 변위만큼 뒤의 종가. 최근 {@code displacement} 봉에는 아직 존재하지 않는다. */
    private Optional<Price> laggingSpanAt(List<Candle> candles, int index) {
        int target = index + displacement;
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
