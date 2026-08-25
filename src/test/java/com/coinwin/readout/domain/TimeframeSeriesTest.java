package com.coinwin.readout.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.domain.Candle;
import com.coinwin.market.domain.CandleInterval;
import com.coinwin.market.domain.CandleSeries;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 곡선 묶음.
 *
 * <p><b>여기서 검사하는 것은 계산이 아니라 고름과 견딤이다.</b> 값이 맞는지는 각 계산기의
 * 골든 테스트가 이미 본다. 이쪽은 "어느 지표를 내는가" 와 "봉이 모자랄 때 어떻게 되는가" 다.
 */
class TimeframeSeriesTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    @DisplayName("봉이 넉넉하면 다섯 지표가 모두 나온다")
    void 다섯_지표() {
        TimeframeSeries series = TimeframeSeries.over(CandleInterval.FOUR_HOURS, 봉(300));

        assertThat(series.ichimoku()).isNotEmpty();
        assertThat(series.bollinger()).isNotEmpty();
        assertThat(series.rsi()).isNotEmpty();
        assertThat(series.macd()).isNotEmpty();
        // **순서가 곧 계약이다.** 화면이 색을 자리로 고르므로 흔들리면 선 색이 바뀐다.
        assertThat(series.movingAverages()).extracting(MovingAverageLine::period)
                .containsExactly(20, 50, 200);
        assertThat(series.movingAverages()).allSatisfy(line ->
                assertThat(line.points()).isNotEmpty());
    }

    /**
     * <b>하나가 모자라다고 전부를 거절하지 않는다.</b> 짧은 주기는 저장된 봉이 적을 수 있고,
     * 그때 200 이동평균 하나 때문에 아무것도 못 그리면 화면이 통째로 빈다.
     */
    @Test
    @DisplayName("봉이 모자라면 그 지표만 빈 목록이고 나머지는 나온다")
    void 모자란_지표만_빈다() {
        TimeframeSeries series = TimeframeSeries.over(CandleInterval.FIFTEEN_MINUTES, 봉(100));

        assertThat(series.movingAverages().get(2).period()).isEqualTo(200);
        assertThat(series.movingAverages().get(2).points()).isEmpty();
        assertThat(series.movingAverages().get(1).points()).isNotEmpty();
        assertThat(series.rsi()).isNotEmpty();
        assertThat(series.macd()).isNotEmpty();
    }

    @Test
    @DisplayName("봉이 아주 적으면 전부 비고도 터지지 않는다")
    void 전부_비어도_성립한다() {
        TimeframeSeries series = TimeframeSeries.over(CandleInterval.ONE_HOUR, 봉(5));

        assertThat(series.ichimoku()).isEmpty();
        assertThat(series.bollinger()).isEmpty();
        assertThat(series.rsi()).isEmpty();
        assertThat(series.macd()).isEmpty();
        assertThat(series.movingAverages()).allSatisfy(line ->
                assertThat(line.points()).isEmpty());
        assertThat(series.candles().size()).isEqualTo(5);
    }

    @Test
    @DisplayName("주기와 캔들을 그대로 들고 있다")
    void 주기와_캔들() {
        TimeframeSeries series = TimeframeSeries.over(CandleInterval.ONE_WEEK, 봉(300));

        assertThat(series.interval()).isEqualTo(CandleInterval.ONE_WEEK);
        assertThat(series.candles().size()).isEqualTo(300);
    }

    @Test
    @DisplayName("캔들 없이는 만들 수 없다")
    void 캔들은_필수다() {
        assertThatThrownBy(() -> TimeframeSeries.over(CandleInterval.ONE_HOUR, null))
                .isInstanceOf(RuntimeException.class);
    }

    /** 톱니로 만든다 — 단조로 흐르면 RSI 가 100 에 붙어 값이 있는지만 보게 된다. */
    private static CandleSeries 봉(int count) {
        return new CandleSeries(IntStream.range(0, count).mapToObj(i -> {
            BigDecimal close = new BigDecimal(60000 + (i % 11) * 130 + i * 7);
            return new Candle(
                    START.plus(Duration.ofHours(i)),
                    Price.of(close),
                    Price.of(close.add(new BigDecimal("120"))),
                    Price.of(close.subtract(new BigDecimal("120"))),
                    Price.of(close),
                    Quantity.of(BigDecimal.ONE));
        }).toList());
    }
}
