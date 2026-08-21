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

    @Test
    void 수량에_적용하면_해당_비중만큼의_수량이_된다() {
        // 총수량 0.00533333 BTC 중 1차 체결분 50%
        assertThat(Percentage.of("50").applyTo(Quantity.of("0.00533333")))
                .isEqualTo(Quantity.of("0.00266667"));
    }

    @Test
    void 수량_적용_결과는_수량_스케일_8자리에서_HALF_UP으로_반올림된다() {
        assertThat(Percentage.of("30").applyTo(Quantity.of("0.00000001")))
                .isEqualTo(Quantity.of("0.00000000"));
        assertThat(Percentage.of("50").applyTo(Quantity.of("0.00000001")))
                .isEqualTo(Quantity.of("0.00000001"));
    }

    @Test
    void 크기_비교는_스케일이_아니라_값으로_한다() {
        assertThat(Percentage.of("100.0001").isGreaterThan(Percentage.of("100"))).isTrue();
        assertThat(Percentage.of("100").isGreaterThan(Percentage.of("100.0000"))).isFalse();
    }

    @Test
    void 개수의_비율도_백분율이다() {
        // 1000 회 시뮬레이션 중 137 회가 손실로 끝났다면 손실 확률 13.7%
        assertThat(Percentage.ofRatio(137, 1000)).isEqualTo(Percentage.of("13.7"));
        assertThat(Percentage.ofRatio(1, 3)).isEqualTo(Percentage.of("33.3333"));
    }

    @Test
    void 전체가_0인_개수_비율은_성립하지_않는다() {
        assertThatThrownBy(() -> Percentage.ofRatio(0, 0))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("0");
    }

    @Test
    void 소수로_바꾸면_100으로_나눈_값이_된다() {
        // 청산가 공식은 MMR 을 소수로 요구한다: 0.4% → 0.004
        assertThat(Percentage.of("0.4").asFraction()).isEqualByComparingTo("0.004");
        assertThat(Percentage.of("100").asFraction()).isEqualByComparingTo("1");
    }
}
