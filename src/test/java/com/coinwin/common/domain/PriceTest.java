package com.coinwin.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PriceTest {

    @Test
    void 스케일이_다른_같은_값은_동등하다() {
        assertThat(Price.of("64000.5")).isEqualTo(Price.of("64000.50"));
    }

    @Test
    void 소수점_셋째자리는_HALF_UP으로_반올림된다() {
        assertThat(Price.of("64000.005").value()).isEqualByComparingTo("64000.01");
        assertThat(Price.of("64000.004").value()).isEqualByComparingTo("64000.00");
    }

    @Test
    void 스케일은_항상_2로_유지된다() {
        assertThat(Price.of("64000").value().scale()).isEqualTo(2);
        assertThat(Price.of("64000.123456").value().scale()).isEqualTo(2);
    }

    @Test
    void 음수_가격은_거부된다() {
        assertThatThrownBy(() -> Price.of("-1"))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("가격");
    }

    @Test
    void 가격_0은_허용된다() {
        assertThat(Price.of("0").value()).isEqualByComparingTo("0.00");
    }

    @Test
    void null은_거부된다() {
        assertThatThrownBy(() -> Price.of((BigDecimal) null))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> Price.of((String) null))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void 숫자가_아닌_문자열은_거부된다() {
        assertThatThrownBy(() -> Price.of("육만사천"))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("형식");
    }

    @Test
    void BigDecimal로도_생성할_수_있고_결과는_문자열_생성과_같다() {
        assertThat(Price.of(new BigDecimal("64000.5"))).isEqualTo(Price.of("64000.50"));
    }

    @Test
    void 두_가격의_간격은_1단위당_금액이다() {
        // |평단 59,000 - 손절 56,000| = 1 BTC 당 3,000 USDT 손실
        assertThat(Price.of("59000").absoluteDifference(Price.of("56000")))
                .isEqualTo(Money.of("3000"));
    }

    @Test
    void 간격은_어느_쪽에서_빼도_같다() {
        assertThat(Price.of("56000").absoluteDifference(Price.of("59000")))
                .isEqualTo(Price.of("59000").absoluteDifference(Price.of("56000")));
    }

    @Test
    void 가격은_1단위의_금액으로_바꿀_수_있다() {
        assertThat(Price.of("59000").asAmount()).isEqualTo(Money.of("59000"));
    }

    @Test
    void 배수를_곱하면_가격이_된다() {
        // 청산가 계수 0.904 = 1 - 1/10 + 0.4%
        assertThat(Price.of("60000").multipliedBy(new BigDecimal("0.904")))
                .isEqualTo(Price.of("54240"));
    }

    @Test
    void 음수가_되는_배수는_거부된다() {
        assertThatThrownBy(() -> Price.of("60000").multipliedBy(new BigDecimal("-0.5")))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void 크기_비교는_스케일이_아니라_값으로_한다() {
        assertThat(Price.of("56000.0").isBelow(Price.of("56000.00"))).isFalse();
        assertThat(Price.of("56000").isAbove(Price.of("56000.00"))).isFalse();
        assertThat(Price.of("55999.99").isBelow(Price.of("56000"))).isTrue();
        assertThat(Price.of("56000.01").isAbove(Price.of("56000"))).isTrue();
    }
}
