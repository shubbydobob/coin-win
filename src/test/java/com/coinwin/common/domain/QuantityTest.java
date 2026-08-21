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

    @Test
    void 수량에_단가를_곱하면_총액이_된다() {
        // 명목가 = 0.00533333 BTC × 59,000 USDT = 314.67 (스케일 2, HALF_UP)
        assertThat(Quantity.of("0.00533333").times(Money.of("59000")))
                .isEqualTo(Money.of("314.67"));
    }

    @Test
    void 총액은_금액_스케일_2자리에서_HALF_UP으로_반올림된다() {
        // 0.00266667 × 4000 = 10.66668 → 10.67
        assertThat(Quantity.of("0.00266667").times(Money.of("4000")))
                .isEqualTo(Money.of("10.67"));
    }

    @Test
    void 등분은_총액을_반올림하기_전에_나눈다() {
        // 정확한 총액 314.6665233333 ÷ 2 = 157.33
        // 총액을 먼저 314.67 로 반올림한 뒤 나누면 157.34 가 되어 1센트 어긋난다
        assertThat(Quantity.of("0.00533333").times(Money.of("59000.01"), 2))
                .isEqualTo(Money.of("157.33"));
        assertThat(Quantity.of("0.00533333").times(Money.of("59000.01")))
                .isEqualTo(Money.of("314.67"));
    }

    @Test
    void 영_등분은_거부된다() {
        assertThatThrownBy(() -> Quantity.of("0.01").times(Money.of("60000"), 0))
                .isInstanceOf(InvalidValueException.class);
    }
}
