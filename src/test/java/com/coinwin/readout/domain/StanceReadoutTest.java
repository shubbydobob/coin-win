package com.coinwin.readout.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.domain.Candle;
import com.coinwin.market.domain.CandleInterval;
import com.coinwin.market.domain.CandleSeries;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.IntToDoubleFunction;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 지표가 선 자리.
 *
 * <p><b>검사하는 것은 판정이 정의를 따르는가까지다.</b> "그러니 롱이 유리하다" 는 이 코드가
 * 말하지 않으므로 잴 것도 없다 — 그 부류는 {@code docs/adr/021} 이 7년 15,110봉에서 반증했다.
 *
 * <p>단조 상승·하락으로 만들면 다섯이 한쪽으로 몰리므로 방향이 실제로 갈리는지 볼 수 있다.
 */
class StanceReadoutTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    @DisplayName("계속 오르면 볼린저를 뺀 넷이 롱 쪽에 선다")
    void 오르는_장() {
        List<IndicatorStance> stances = 자리(i -> 60000 + i * 60.0);

        assertThat(stances).extracting(IndicatorStance::indicator)
                .containsExactly(IndicatorKind.ICHIMOKU, IndicatorKind.BOLLINGER,
                        IndicatorKind.MOVING_AVERAGE, IndicatorKind.RSI, IndicatorKind.MACD);
        assertThat(stances).extracting(IndicatorStance::indicator, IndicatorStance::stance)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple(IndicatorKind.ICHIMOKU, Stance.LONG),
                        // **꾸준한 상승은 밴드를 뚫지 않는다.** 기울기가 일정하면 표준편차도
                        // 일정해서 종가가 상단 안쪽에 머문다 — 처음에 다섯 다 롱일 것으로
                        // 적었다가 이 테스트가 잡았다. 밴드는 추세가 아니라 **급함**을 잰다.
                        org.assertj.core.api.Assertions.tuple(IndicatorKind.BOLLINGER, Stance.NEUTRAL),
                        org.assertj.core.api.Assertions.tuple(IndicatorKind.MOVING_AVERAGE, Stance.LONG),
                        org.assertj.core.api.Assertions.tuple(IndicatorKind.RSI, Stance.LONG),
                        // MACD 는 여기서 빼지 않고 그대로 두되, 완전한 직선에서 이 값이
                        // 무엇으로 수렴하는지는 아래 별도 테스트가 말한다.
                        org.assertj.core.api.Assertions.tuple(IndicatorKind.MACD, Stance.LONG));
    }

    /**
     * <b>기울기가 완전히 일정하면 MACD 히스토그램이 0 으로 수렴한다.</b> 두 EMA 의 차가
     * 상수가 되고 시그널도 같은 상수로 가므로 그 차가 사라진다 — 합성 데이터의 성질이지
     * 구현 문제가 아니다.
     *
     * <p>이 테스트가 없으면 다음 사람이 "내리는 장인데 MACD 가 중립이네" 를 버그로 읽는다.
     * 실제로 그렇게 읽고 기댓값을 SHORT 로 적었다가 테스트가 잡았다.
     */
    @Test
    @DisplayName("완전한 직선에서는 MACD 가 어느 쪽도 아니게 된다")
    void 직선에서의_MACD() {
        assertThat(자리(i -> 90000 - i * 60.0))
                .filteredOn(stance -> stance.indicator() == IndicatorKind.MACD)
                .extracting(IndicatorStance::stance)
                .containsExactly(Stance.NEUTRAL);
    }

    /** 마지막에 꺾이면 부호가 분명해진다. 실제 캔들은 언제나 이쪽에 가깝다. */
    @Test
    @DisplayName("끝에서 꺾이면 MACD 가 그쪽에 선다")
    void 꺾이면_MACD가_갈린다() {
        assertThat(자리(i -> i < 250 ? 60000 + i * 60.0 : 75000 - (i - 250) * 400.0))
                .filteredOn(stance -> stance.indicator() == IndicatorKind.MACD)
                .extracting(IndicatorStance::stance)
                .containsExactly(Stance.SHORT);
    }

    @Test
    @DisplayName("계속 내리면 볼린저를 뺀 넷이 숏 쪽에 선다")
    void 내리는_장() {
        assertThat(자리(i -> 90000 - i * 60.0))
                .extracting(IndicatorStance::indicator, IndicatorStance::stance)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple(IndicatorKind.ICHIMOKU, Stance.SHORT),
                        org.assertj.core.api.Assertions.tuple(IndicatorKind.BOLLINGER, Stance.NEUTRAL),
                        org.assertj.core.api.Assertions.tuple(IndicatorKind.MOVING_AVERAGE, Stance.SHORT),
                        org.assertj.core.api.Assertions.tuple(IndicatorKind.RSI, Stance.SHORT),
                        org.assertj.core.api.Assertions.tuple(IndicatorKind.MACD, Stance.NEUTRAL));
    }

    /**
     * <b>말할 수 없는 것과 어느 쪽도 아닌 것은 다르다.</b> 봉이 모자라면 UNKNOWN 이고,
     * 그것을 NEUTRAL 로 적으면 "가운데 있다" 는 없는 사실이 생긴다.
     */
    @Test
    @DisplayName("봉이 모자라면 중립이 아니라 말할 수 없음이다")
    void 봉이_모자라면() {
        TimeframeSeries series = TimeframeSeries.over(CandleInterval.ONE_HOUR, 봉(5, i -> 60000));

        assertThat(series.stances()).extracting(IndicatorStance::stance)
                .containsOnly(Stance.UNKNOWN);
        assertThat(series.stances()).extracting(IndicatorStance::statement)
                .containsOnly("봉이 모자라 말할 수 없다");
    }

    @Test
    @DisplayName("평평하면 이동평균이 순서대로 놓이지 않는다")
    void 평평한_장() {
        List<IndicatorStance> stances = 자리(i -> 60000);

        assertThat(stances).filteredOn(stance -> stance.indicator() == IndicatorKind.MOVING_AVERAGE)
                .extracting(IndicatorStance::stance)
                .containsExactly(Stance.NEUTRAL);
    }

    @Test
    @DisplayName("문장은 무엇을 하라고 말하지 않는다")
    void 문장은_지시하지_않는다() {
        for (IndicatorStance stance : 자리(i -> 60000 + i * 60.0)) {
            assertThat(stance.statement())
                    .doesNotContain("유리", "불리", "추천", "노려", "진입", "매수", "매도");
        }
    }

    private static List<IndicatorStance> 자리(IntToDoubleFunction close) {
        return TimeframeSeries.over(CandleInterval.FOUR_HOURS, 봉(300, close)).stances();
    }

    private static CandleSeries 봉(int count, IntToDoubleFunction close) {
        return new CandleSeries(IntStream.range(0, count).mapToObj(i -> {
            BigDecimal value = BigDecimal.valueOf(close.applyAsDouble(i));
            return new Candle(
                    START.plus(Duration.ofHours(i)),
                    Price.of(value),
                    Price.of(value.add(BigDecimal.ONE)),
                    Price.of(value.subtract(BigDecimal.ONE)),
                    Price.of(value),
                    Quantity.of(BigDecimal.ONE));
        }).toList());
    }
}
