package com.coinwin.indicator.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 거리를 ATR 로 잰 배수.
 *
 * <p><b>왜 이 단위인가.</b> 가격 차를 USDT 로 두면 60,000 시절과 100,000 시절이 비교되지
 * 않고, % 로 두면 변동성이 죽은 구간과 살아 있는 구간이 비교되지 않는다. 대와 손절 버퍼가
 * 이미 ATR 단위로 정해져 있다.
 */
class AtrMultipleTest {

    private static final Money ATR = Money.of(new BigDecimal("500"));

    @Test
    @DisplayName("ATR 의 몇 배인가")
    void 배수() {
        assertThat(AtrMultiple.of(Money.of(new BigDecimal("750")), ATR).value())
                .isEqualByComparingTo("1.50");
    }

    /**
     * <b>부호가 절반이다.</b> 전환선이 기준선 위인지 아래인지가 이 값이 말하는 것의 절반이고,
     * 절대값만 남기면 그 절반이 사라진다.
     */
    @Test
    @DisplayName("아래쪽 거리는 음수로 남는다")
    void 부호를_잃지_않는다() {
        assertThat(AtrMultiple.of(Money.of(new BigDecimal("-250")), ATR).value())
                .isEqualByComparingTo("-0.50");
    }

    @Test
    @DisplayName("두 가격의 차를 ATR 로 잰다. 뺀 순서가 부호를 정한다")
    void 두_가격_사이() {
        Price high = Price.of(new BigDecimal("61000"));
        Price low = Price.of(new BigDecimal("60000"));

        assertThat(AtrMultiple.between(high, low, ATR).value()).isEqualByComparingTo("2.00");
        assertThat(AtrMultiple.between(low, high, ATR).value()).isEqualByComparingTo("-2.00");
    }

    /** 소수 둘에서 끊는다. 0.005 는 위로 간다 — 이 저장소의 반올림은 HALF_UP 하나뿐이다. */
    @Test
    @DisplayName("소수 둘에서 반올림한다")
    void 반올림() {
        assertThat(AtrMultiple.of(Money.of(new BigDecimal("2.53")), ATR).value())
                .isEqualByComparingTo("0.01");
    }

    /**
     * <b>ATR 이 0 이면 배수를 말할 수 없다.</b> 0 으로 나눈 값을 0 이나 큰 수로 적으면 그것이
     * 지표처럼 보인다 — 「말할 수 없는 것을 0 으로 적지 않는다」는 이 저장소의 규칙이다.
     */
    @Test
    @DisplayName("ATR 이 0 이면 거절한다")
    void 변동성이_0_이면() {
        assertThatThrownBy(() -> AtrMultiple.of(Money.of(BigDecimal.ONE), Money.of(BigDecimal.ZERO)))
                .isInstanceOf(InvalidIndicatorException.class)
                .hasMessageContaining("ATR");
    }
}
