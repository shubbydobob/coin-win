package com.coinwin.projection.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import java.util.List;
import org.junit.jupiter.api.Test;

class EquityCurveTest {

    private static EquityCurve 곡선(String... 자산) {
        return new EquityCurve(List.of(자산).stream().map(Money::of).toList());
    }

    @Test
    void 첫_점은_초기_자본이고_마지막_점은_최종_자산이다() {
        EquityCurve curve = 곡선("1000", "1200", "900", "1100");

        assertThat(curve.initialCapital()).isEqualTo(Money.of("1000"));
        assertThat(curve.finalEquity()).isEqualTo(Money.of("1100"));
    }

    @Test
    void 거래_수는_점의_수보다_하나_적다() {
        // 첫 점은 거래 이전의 상태다
        assertThat(곡선("1000", "1200", "900", "1100").trades()).isEqualTo(3);
        assertThat(곡선("1000").trades()).isZero();
    }

    @Test
    void 최대낙폭은_고점_대비_최대_하락폭이다() {
        // 고점 1200 에서 900 까지 = 300 / 1200 = 25%
        assertThat(곡선("1000", "1200", "900", "1100").maxDrawdown())
                .isEqualTo(Percentage.of("25"));
    }

    @Test
    void 낙폭은_직전_고점_기준이므로_나중_고점의_하락과_구분된다() {
        // 1000 → 800 은 20%, 그 뒤 2000 → 1800 은 10%. 둘 중 큰 쪽이 최대낙폭이다
        assertThat(곡선("1000", "800", "2000", "1800").maxDrawdown())
                .isEqualTo(Percentage.of("20"));
    }

    @Test
    void 고점을_경신하기만_하면_낙폭은_0이다() {
        assertThat(곡선("1000", "1100", "1200").maxDrawdown()).isEqualTo(Percentage.of("0"));
    }

    @Test
    void 최종_자산이_초기_자본에_못_미치면_손실로_끝난_것이다() {
        assertThat(곡선("1000", "1200", "999.99").lostMoney()).isTrue();
        assertThat(곡선("1000", "800", "1000").lostMoney()).isFalse();
    }

    @Test
    void 점이_하나도_없는_곡선은_거부된다() {
        assertThatThrownBy(() -> new EquityCurve(List.of()))
                .isInstanceOf(InvalidProjectionException.class)
                .hasMessageContaining("초기 자본");
    }

    @Test
    void null은_거부된다() {
        assertThatThrownBy(() -> new EquityCurve(null))
                .isInstanceOf(InvalidValueException.class);
    }
}
