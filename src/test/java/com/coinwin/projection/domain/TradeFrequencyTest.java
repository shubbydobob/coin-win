package com.coinwin.projection.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import org.junit.jupiter.api.Test;

class TradeFrequencyTest {

    @Test
    void 총_거래_수는_주당_거래_수에_기간을_곱한_것이다() {
        // 주 2회 × 50주 = 100 거래
        assertThat(new TradeFrequency(2, 50).totalTrades()).isEqualTo(100);
    }

    @Test
    void 주당_거래_수와_기간은_각각_1_이상이어야_한다() {
        assertThatThrownBy(() -> new TradeFrequency(0, 50))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("주당 거래 수");
        assertThatThrownBy(() -> new TradeFrequency(2, 0))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("기간");
    }

    /** 시뮬레이션 비용은 거래 수 × 시행 횟수로 늘어난다. 상한이 없으면 요청 하나가 서버를 잡는다. */
    @Test
    void 총_거래_수에는_상한이_있다() {
        assertThat(new TradeFrequency(20, 500).totalTrades()).isEqualTo(10_000);
        assertThatThrownBy(() -> new TradeFrequency(20, 501))
                .isInstanceOf(InvalidProjectionException.class)
                .hasMessageContaining("10000");
    }
}
