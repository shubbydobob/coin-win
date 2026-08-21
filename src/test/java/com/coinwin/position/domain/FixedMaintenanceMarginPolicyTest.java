package com.coinwin.position.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Percentage;
import org.junit.jupiter.api.Test;

class FixedMaintenanceMarginPolicyTest {

    @Test
    void 기본값은_BTCUSDT_근사치_0_4퍼센트다() {
        assertThat(FixedMaintenanceMarginPolicy.btcUsdtApproximation().rate())
                .isEqualTo(Percentage.of("0.4"));
    }

    @Test
    void 다른_유지증거금률로도_만들_수_있다() {
        assertThat(new FixedMaintenanceMarginPolicy(Percentage.of("1.25")).rate())
                .isEqualTo(Percentage.of("1.25"));
    }

    @Test
    void 유지증거금률이_없으면_거부된다() {
        assertThatThrownBy(() -> new FixedMaintenanceMarginPolicy(null))
                .isInstanceOf(InvalidValueException.class);
    }
}
