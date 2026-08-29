package com.coinwin.indicator.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.domain.Candle;
import com.coinwin.market.domain.CandleSeries;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RSI 골든 테스트.
 *
 * <p><b>기댓값은 트레이딩뷰 Pine 원문({@code STD;RSI/24.0})의 식을 따로 구현해 얻었다.</b>
 * 차트의 수를 눈으로 옮겨 적으면 특정 시점만 증명되고, 그 시점이 맞았다는 것조차 확인할 수
 * 없다. 일목 변위를 한 칸 틀렸다가 같은 방법으로 잡은 전례가 있다.
 *
 * <p><b>이 테스트가 증명하는 것과 못 하는 것을 갈라 둔다.</b> 증명하는 것은 "구현이 그 식을
 * 정확히 따른다" 이고, 못 하는 것은 "그 식이 트레이딩뷰가 실제로 그리는 것이다" 다. 뒤쪽은
 * 소스를 읽어서 확정했고 그 인용을 {@link RelativeStrengthIndex} 문서에 남겨 두었다.
 */
class RelativeStrengthIndexTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    @DisplayName("오르기만 하면 100 이다 — 내린 폭이 없으면 나눗셈이 성립하지 않는다")
    void 오르기만_하면_100() {
        CandleSeries series = series(IntStream.range(0, 20).mapToObj(i -> 100 + i).toList());

        assertThat(RelativeStrengthIndex.standard().over(series))
                .first()
                .extracting(IndicatorPoint::value)
                .isEqualTo(Percentage.of(new BigDecimal("100")));
    }

    @Test
    @DisplayName("내리기만 하면 0 이다")
    void 내리기만_하면_0() {
        CandleSeries series = series(IntStream.range(0, 20).mapToObj(i -> 200 - i).toList());

        assertThat(RelativeStrengthIndex.standard().over(series))
                .first()
                .extracting(IndicatorPoint::value)
                .isEqualTo(Percentage.of(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("톱니 17봉의 값이 Pine 식과 소수 넷째 자리까지 같다")
    void 톱니_17봉_골든값() {
        CandleSeries series = closes(
                "100 101 100.5 102 101 103 102.5 104 103 105 104.5 106 105 107 106 108 107");

        assertThat(RelativeStrengthIndex.standard().over(series))
                .extracting(point -> point.value().value().toPlainString())
                .containsExactly("67.6471", "71.2851", "67.2153");
    }

    @Test
    @DisplayName("첫 값은 period + 1 번째 봉에 놓인다 — 변화는 봉보다 하나 적다")
    void 첫_값의_자리() {
        CandleSeries series = series(IntStream.range(0, 20).mapToObj(i -> 100 + i).toList());

        assertThat(RelativeStrengthIndex.standard().over(series))
                .first()
                .extracting(IndicatorPoint::at)
                .isEqualTo(START.plus(Duration.ofHours(14)));
    }

    @Test
    @DisplayName("봉이 워밍업보다 적으면 판독하지 않는다")
    void 봉이_모자라면_거부한다() {
        CandleSeries series = series(IntStream.range(0, 14).mapToObj(i -> 100 + i).toList());

        assertThatThrownBy(() -> RelativeStrengthIndex.standard().over(series))
                .isInstanceOf(InsufficientCandlesException.class);
    }

    @Test
    @DisplayName("기간은 2 미만일 수 없다")
    void 기간_경계() {
        assertThatThrownBy(() -> new RelativeStrengthIndex(1))
                .isInstanceOf(RuntimeException.class);
    }

    private static CandleSeries closes(String text) {
        return series(Arrays.stream(text.split(" ")).map(BigDecimal::new).toList());
    }

    private static CandleSeries series(List<? extends Number> closes) {
        return new CandleSeries(IntStream.range(0, closes.size())
                .mapToObj(i -> candle(i, new BigDecimal(closes.get(i).toString())))
                .toList());
    }

    /** RSI 는 종가만 본다. 나머지 칸은 캔들이 성립하도록만 채운다. */
    private static Candle candle(int index, BigDecimal close) {
        return new Candle(
                START.plus(Duration.ofHours(index)),
                Price.of(close),
                Price.of(close.add(BigDecimal.ONE)),
                Price.of(close.subtract(BigDecimal.ONE)),
                Price.of(close),
                Quantity.of(BigDecimal.ONE));
    }
}
