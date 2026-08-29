package com.coinwin.indicator.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.domain.Candle;
import com.coinwin.market.domain.CandleSeries;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MACD 골든 테스트.
 *
 * <p><b>기댓값은 트레이딩뷰 Pine 원문({@code STD;MACD/18.0})의 식을 따로 구현해 얻었다.</b>
 * 원문에서 확정한 것은 셋이다 — 넷 다 EMA 라는 것, 시그널이 MACD 의 EMA 라는 것, 히스토그램이
 * 그 차라는 것.
 *
 * <p><b>자리 맞추기를 겨냥한 테스트가 하나 있다.</b> 빠른 EMA 와 느린 EMA 는 시작하는 봉이
 * 다르고, 그대로 빼면 서로 다른 봉의 값을 빼게 된다. 그 오차는 그럴듯한 곡선으로 나오므로
 * 값을 눈으로 봐서는 알 수 없다.
 */
class MacdTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    /** 파이썬으로 같은 식을 따로 구현해 얻은 값. 소수 넷째 자리까지 맞댄다. */
    @Test
    @DisplayName("40봉의 세 값이 Pine 식과 소수 넷째 자리까지 같다")
    void 골든값() {
        List<IndicatorPoint<MacdValue>> points = Macd.standard().over(합성(40));

        assertThat(points).hasSize(7);
        assertThat(points).extracting(point -> 넷째(point.value().macd()))
                .containsExactly("3.6980", "3.9052", "3.5843", "3.4117", "3.3573", "3.3960", "3.5073");
        assertThat(points).extracting(point -> 넷째(point.value().signal()))
                .containsExactly("3.5917", "3.6544", "3.6404", "3.5946", "3.5472", "3.5169", "3.5150");
        assertThat(points).extracting(point -> 넷째(point.value().histogram()))
                .containsExactly("0.1063", "0.2508", "-0.0561", "-0.1829", "-0.1899", "-0.1209", "-0.0077");
    }

    @Test
    @DisplayName("히스토그램은 언제나 MACD 에서 시그널을 뺀 것이다")
    void 히스토그램은_나머지_둘의_차다() {
        assertThat(Macd.standard().over(합성(40)))
                .allSatisfy(point -> assertThat(point.value().histogram())
                        .isEqualByComparingTo(
                                point.value().macd().subtract(point.value().signal())));
    }

    /**
     * <b>자리 맞추기 검사.</b> 종가가 한 값으로 고정이면 두 EMA 가 모두 그 값이므로 MACD 는
     * 정확히 0 이다. 자리를 잘못 맞춰도 값이 같아 0 이 나오므로, 이 케이스는 부호나 배열
     * 인덱스가 통째로 어긋난 경우만 잡는다 — 골든값 쪽이 나머지를 잡는다.
     */
    @Test
    @DisplayName("종가가 움직이지 않으면 세 값이 모두 0 이다")
    void 평평하면_0() {
        CandleSeries series = 종가들(IntStream.range(0, 40).mapToObj(i -> new BigDecimal("100")).toList());

        assertThat(Macd.standard().over(series))
                .allSatisfy(point -> {
                    assertThat(point.value().macd()).isEqualByComparingTo(BigDecimal.ZERO);
                    assertThat(point.value().signal()).isEqualByComparingTo(BigDecimal.ZERO);
                    assertThat(point.value().histogram()).isEqualByComparingTo(BigDecimal.ZERO);
                });
    }

    @Test
    @DisplayName("첫 값은 느린 구간과 시그널 구간을 합친 자리에 놓인다")
    void 첫_값의_자리() {
        assertThat(Macd.standard().over(합성(40)))
                .first()
                .extracting(IndicatorPoint::at)
                .isEqualTo(START.plus(Duration.ofHours(33)));
    }

    @Test
    @DisplayName("봉이 워밍업보다 적으면 판독하지 않는다")
    void 봉이_모자라면_거부한다() {
        assertThatThrownBy(() -> Macd.standard().over(합성(33)))
                .isInstanceOf(InsufficientCandlesException.class);
    }

    @Test
    @DisplayName("빠른 구간이 느린 구간보다 짧지 않으면 거부한다")
    void 구간_순서() {
        assertThatThrownBy(() -> new Macd(26, 12, 9))
                .isInstanceOf(InvalidIndicatorException.class);
    }

    private static String 넷째(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP).toPlainString();
    }

    /** 파이썬 쪽과 같은 식으로 만든 합성 종가: {@code 100 + (i % 7) − 3 + i / 2}. */
    private static CandleSeries 합성(int count) {
        return 종가들(IntStream.range(0, count)
                .mapToObj(i -> new BigDecimal(100 + (i % 7) - 3)
                        .add(new BigDecimal(i).divide(new BigDecimal("2"))))
                .toList());
    }

    private static CandleSeries 종가들(List<BigDecimal> closes) {
        return new CandleSeries(IntStream.range(0, closes.size())
                .mapToObj(i -> new Candle(
                        START.plus(Duration.ofHours(i)),
                        Price.of(closes.get(i)),
                        Price.of(closes.get(i).add(BigDecimal.ONE)),
                        Price.of(closes.get(i).subtract(BigDecimal.ONE)),
                        Price.of(closes.get(i)),
                        Quantity.of(BigDecimal.ONE)))
                .toList());
    }
}
