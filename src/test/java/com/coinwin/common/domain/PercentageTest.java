package com.coinwin.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PercentageTest {

    @Test
    void 스케일이_다른_같은_값은_동등하다() {
        assertThat(Percentage.of("50")).isEqualTo(Percentage.of("50.0000"));
    }

    @Test
    void 소수점_다섯째자리는_HALF_UP으로_반올림된다() {
        assertThat(Percentage.of("0.00005").value()).isEqualByComparingTo("0.0001");
        assertThat(Percentage.of("0.00004").value()).isEqualByComparingTo("0.0000");
    }

    @Test
    void 스케일은_항상_4로_유지된다() {
        assertThat(Percentage.of("50").value().scale()).isEqualTo(4);
    }

    @Test
    void 음수_비율은_거부된다() {
        assertThatThrownBy(() -> Percentage.of("-1"))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("비율");
    }

    @Test
    void null은_거부된다() {
        assertThatThrownBy(() -> Percentage.of((BigDecimal) null))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void BigDecimal로도_생성할_수_있고_결과는_문자열_생성과_같다() {
        assertThat(Percentage.of(new BigDecimal("2.5"))).isEqualTo(Percentage.of("2.5000"));
    }

    @Test
    void 금액에_적용하면_해당_비율만큼의_금액이_된다() {
        // 계좌 800 USDT 의 2% = 16 USDT (리스크 금액 계산의 첫 단계)
        assertThat(Percentage.of("2").applyTo(Money.of("800"))).isEqualTo(Money.of("16"));
    }

    @Test
    void 금액_적용_결과는_금액_스케일_2자리에서_HALF_UP으로_반올림된다() {
        // 333.33 의 1.5% = 4.99995 → 5.00
        assertThat(Percentage.of("1.5").applyTo(Money.of("333.33")))
                .isEqualTo(Money.of("5.00"));
    }
}
