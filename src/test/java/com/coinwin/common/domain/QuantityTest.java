package com.coinwin.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class QuantityTest {

    @Test
    void 스케일이_다른_같은_값은_동등하다() {
        assertThat(Quantity.of("0.0125")).isEqualTo(Quantity.of("0.01250000"));
    }

    @Test
    void 소수점_아홉째자리는_HALF_UP으로_반올림된다() {
        assertThat(Quantity.of("0.000000005").value()).isEqualByComparingTo("0.00000001");
        assertThat(Quantity.of("0.000000004").value()).isEqualByComparingTo("0.00000000");
    }

    @Test
    void 수량은_BTC_기준이므로_스케일이_항상_8로_유지된다() {
        assertThat(Quantity.of("1").value().scale()).isEqualTo(8);
        assertThat(Quantity.of("0.0125").value().scale()).isEqualTo(8);
    }

    @Test
    void 음수_수량은_거부된다() {
        assertThatThrownBy(() -> Quantity.of("-0.01"))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("수량");
    }

    @Test
    void null은_거부된다() {
        assertThatThrownBy(() -> Quantity.of((BigDecimal) null))
                .isInstanceOf(InvalidValueException.class);
    }
}
