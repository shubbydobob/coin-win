package com.coinwin.indicator.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.domain.Candle;
import com.coinwin.market.domain.CandleSeries;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 이동평균선 테스트.
 *
 * <p><b>기댓값이 손으로 풀린다.</b> 1~5 의 3봉 단순평균은 2·3·4 이고, 등차수열에서는 지수
 * 평균도 같은 값이 된다 — 첫 값이 단순평균으로 앉고 그 뒤로 같은 간격이 이어지기 때문이다.
 * 그래서 두 종류를 <b>같은 입력으로</b> 재면 "종류를 잘못 골랐다" 는 잡히지 않는다.
 * 그 구분은 등차가 아닌 입력이 한다.
 */
class MovingAverageTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    @DisplayName("단순평균 3봉은 구간의 산술평균이다")
    void 단순평균() {
        assertThat(MovingAverage.simple(3).over(종가들("1 2 3 4 5")))
                .extracting(point -> point.value().value().toPlainString())
                .containsExactly("2.00", "3.00", "4.00");
    }

    @Test
    @DisplayName("등차수열에서는 지수평균도 단순평균과 같다")
    void 등차수열에서는_둘이_같다() {
        assertThat(MovingAverage.exponential(3).over(종가들("1 2 3 4 5")))
                .extracting(point -> point.value().value().toPlainString())
                .containsExactly("2.00", "3.00", "4.00");
    }

    /**
     * <b>종류를 가르는 케이스.</b> 마지막에 튀는 값이 오면 지수평균이 더 크게 반응한다 —
     * {@code alpha = 2/(3+1) = 0.5} 이므로 새 값이 절반을 차지한다.
     *
     * <p>단순: (2 + 3 + 13) / 3 = 6.00 · 지수: 0.5 × 13 + 0.5 × 2 = 7.50
     *
     * <p><b>지수 쪽 씨앗이 2 이지 2.5 가 아니다</b> — 앞 3봉(1·2·3)의 단순평균이다.
     * 처음에 2.5 로 잡아 7.75 를 적었고 테스트가 그것을 잡았다. 구현이 아니라
     * 기댓값이 틀린 경우이고, 골든 테스트가 실제로 하는 일이 이것이다.
     */
    @Test
    @DisplayName("튀는 값에는 지수평균이 더 크게 반응한다")
    void 두_종류가_갈린다() {
        CandleSeries series = 종가들("1 2 3 13");

        assertThat(MovingAverage.simple(3).over(series))
                .last()
                .extracting(point -> point.value().value().toPlainString())
                .isEqualTo("6.00");
        assertThat(MovingAverage.exponential(3).over(series))
                .last()
                .extracting(point -> point.value().value().toPlainString())
                .isEqualTo("7.50");
    }

    @Test
    @DisplayName("첫 값은 period 번째 봉에 놓인다")
    void 첫_값의_자리() {
        assertThat(MovingAverage.simple(3).over(종가들("1 2 3 4 5")))
                .first()
                .extracting(IndicatorPoint::at)
                .isEqualTo(START.plus(Duration.ofHours(2)));
    }

    @Test
    @DisplayName("봉이 구간보다 적으면 판독하지 않는다")
    void 봉이_모자라면_거부한다() {
        assertThatThrownBy(() -> MovingAverage.simple(5).over(종가들("1 2 3")))
                .isInstanceOf(InsufficientCandlesException.class);
    }

    @Test
    @DisplayName("기간은 1 미만일 수 없다")
    void 기간_경계() {
        assertThatThrownBy(() -> MovingAverage.simple(0)).isInstanceOf(RuntimeException.class);
    }

    private static CandleSeries 종가들(String text) {
        List<BigDecimal> closes = List.of(text.split(" ")).stream().map(BigDecimal::new).toList();
        return new CandleSeries(IntStream.range(0, closes.size())
                .mapToObj(i -> new Candle(
                        START.plus(Duration.ofHours(i)),
                        Price.of(closes.get(i)),
                        Price.of(closes.get(i).add(BigDecimal.ONE)),
                        Price.of(closes.get(i)),
                        Price.of(closes.get(i)),
                        Quantity.of(BigDecimal.ONE)))
                .toList());
    }
}
