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
}
