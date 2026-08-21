package com.coinwin.backtest.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.backtest.BacktestFixtures;
import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Price;
import com.coinwin.market.domain.CandleSeries;
import java.util.List;
import org.junit.jupiter.api.Test;

class PivotDetectorTest {

    private static final PivotDetector DETECTOR = new PivotDetector(2);

    /**
     * 종가 열 {@code 100 101 105 101 100} — 가운데 봉만 좌우 2봉보다 높다.
     * half 1 이므로 고가는 106, 저가는 104.
     */
    @Test
    void 좌우_N봉보다_높은_고가만_스윙_고점이다() {
        List<Pivot> pivots = DETECTOR.over(BacktestFixtures.wave(1, 100, 101, 105, 101, 100));

        assertThat(pivots).singleElement().satisfies(pivot -> {
            assertThat(pivot.kind()).isEqualTo(PivotKind.SWING_HIGH);
            assertThat(pivot.price()).isEqualTo(Price.of("106"));
            assertThat(pivot.at()).isEqualTo(BacktestFixtures.hour(2));
        });
    }

    @Test
    void 좌우_N봉보다_낮은_저가는_스윙_저점이다() {
        List<Pivot> pivots = DETECTOR.over(BacktestFixtures.wave(1, 105, 104, 100, 104, 105));

        assertThat(pivots).singleElement().satisfies(pivot -> {
            assertThat(pivot.kind()).isEqualTo(PivotKind.SWING_LOW);
            assertThat(pivot.price()).isEqualTo(Price.of("99"));
        });
    }

    /**
     * 같은 극값이 둘이면 어느 쪽이 그 자리인지 정해지지 않는다.
     *
     * <p>둘 다 채택하면 같은 가격에 터치가 두 번 찍혀, 실제로는 한 번 반응한 자리가 최소 터치
     * 기준을 통과한다. 대의 강도가 부풀려지는 경로다.
     */
    @Test
    void 같은_극값이_둘이면_피벗이_아니다() {
        // 동률인 두 봉(idx 2·3)이 모두 후보 구간 안에 들도록 7봉으로 둔다
        assertThat(DETECTOR.over(BacktestFixtures.wave(1, 100, 101, 105, 105, 101, 100, 100)))
                .isEmpty();
    }

    /** 판정은 스케일이 아니라 값으로 한다. {@code 105.0} 과 {@code 105.00} 은 동률이다. */
    @Test
    void 스케일만_다른_동률도_피벗이_아니다() {
        CandleSeries series = new CandleSeries(List.of(
                BacktestFixtures.candle(0, "101", "99", "100"),
                BacktestFixtures.candle(1, "101", "99", "100"),
                BacktestFixtures.candle(2, "105.0", "99", "100"),
                BacktestFixtures.candle(3, "105.00", "99", "100"),
                BacktestFixtures.candle(4, "101", "99", "100"),
                BacktestFixtures.candle(5, "101", "99", "100"),
                BacktestFixtures.candle(6, "101", "99", "100")));

        assertThat(DETECTOR.over(series)).isEmpty();
    }

    /**
     * 확정 시각은 {@code i + lookback} 봉의 시각이다. 발생 시각이 아니다.
     *
     * <p>이 구분이 없으면 백테스트가 아직 확정되지 않은 극값을 본다. 룩어헤드를 막는 유일한
     * 장치이므로 값 자체를 못 박는다.
     */
    @Test
    void 확정_시각은_발생_시각보다_lookback_봉_뒤다() {
        Pivot pivot = DETECTOR.over(BacktestFixtures.wave(1, 100, 101, 105, 101, 100)).getFirst();

        assertThat(pivot.at()).isEqualTo(BacktestFixtures.hour(2));
        assertThat(pivot.confirmedAt()).isEqualTo(BacktestFixtures.hour(4));
        assertThat(pivot.isKnownAt(BacktestFixtures.hour(3))).isFalse();
        assertThat(pivot.isKnownAt(BacktestFixtures.hour(4))).isTrue();
        assertThat(pivot.isKnownAt(BacktestFixtures.hour(5))).isTrue();
    }

    /** 좌우 어느 한쪽이라도 모자라면 판정할 수 없다. 앞뒤 {@code lookback} 봉은 후보가 아니다. */
    @Test
    void 양_끝_lookback_봉은_피벗이_될_수_없다() {
        // 첫 봉과 마지막 봉이 각각 최고·최저지만 좌우가 없어 확정되지 않는다
        assertThat(DETECTOR.over(BacktestFixtures.wave(1, 200, 101, 102, 101, 50))).isEmpty();
    }

    @Test
    void 캔들이_모자라면_피벗이_하나도_없다() {
        assertThat(DETECTOR.over(BacktestFixtures.wave(1, 100, 105, 100))).isEmpty();
        assertThat(DETECTOR.over(CandleSeries.empty())).isEmpty();
    }

    /** 고점과 저점이 여러 번 번갈아 나오는 시리즈. 이것이 대를 만드는 실제 입력이다. */
    @Test
    void 오르내리는_시리즈에서는_고점과_저점이_번갈아_나온다() {
        List<Pivot> pivots = DETECTOR.over(BacktestFixtures.wave(
                1, 100, 101, 110, 101, 100, 99, 90, 99, 100, 101, 110, 101, 100));

        assertThat(pivots).extracting(Pivot::kind).containsExactly(
                PivotKind.SWING_HIGH, PivotKind.SWING_LOW, PivotKind.SWING_HIGH);
        assertThat(pivots).extracting(Pivot::price)
                .containsExactly(Price.of("111"), Price.of("89"), Price.of("111"));
    }

    @Test
    void 탐지_폭은_1_이상이어야_한다() {
        assertThatThrownBy(() -> new PivotDetector(0)).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void 확정_시각이_발생_시각보다_앞선_피벗은_거부된다() {
        assertThatThrownBy(() -> new Pivot(BacktestFixtures.hour(5), BacktestFixtures.hour(4),
                Price.of("100"), PivotKind.SWING_HIGH))
                .isInstanceOf(InvalidBacktestException.class)
                .hasMessageContaining("확정");
    }
}
