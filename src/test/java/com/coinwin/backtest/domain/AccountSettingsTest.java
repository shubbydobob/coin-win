package com.coinwin.backtest.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import org.junit.jupiter.api.Test;

class AccountSettingsTest {

    private static final Money START = Money.of("800");
    private static final Percentage RISK = Percentage.of("2");

    private static AccountSettings settings(CapitalMode mode) {
        return new AccountSettings(START, RISK, 10, mode);
    }

    /** 고정 모드는 거래 간 독립이라 전략 자체의 엣지가 깨끗하게 보인다. */
    @Test
    void 고정_모드는_자산이_늘어도_같은_잔고로_사이징한다() {
        assertThat(settings(CapitalMode.FIXED).balanceFor(Money.of("1200"))).isEqualTo(START);
        assertThat(settings(CapitalMode.FIXED).balanceFor(Money.of("400"))).isEqualTo(START);
    }

    /** 복리 모드는 실사용과 같다. 직전 거래까지의 자산이 다음 거래의 크기를 정한다. */
    @Test
    void 복리_모드는_현재_자산을_그대로_잔고로_쓴다() {
        assertThat(settings(CapitalMode.COMPOUND).balanceFor(Money.of("1200")))
                .isEqualTo(Money.of("1200"));
    }

    /**
     * 자산이 0 이하로 내려가면 거래를 이어 갈 수 없다.
     *
     * <p>{@code RiskBudget} 이 음수 잔고에서 던지므로 그것을 예외로 흘리면 백테스트가 중간에
     * 멈춘다. 여기서 물어보고 멈추면 그때까지의 결과는 그대로 남는다.
     */
    @Test
    void 자산이_0_이하면_더_이상_거래할_수_없다() {
        AccountSettings compound = settings(CapitalMode.COMPOUND);

        assertThat(compound.canTradeWith(Money.of("0.01"))).isTrue();
        assertThat(compound.canTradeWith(Money.of("0"))).isFalse();
        assertThat(compound.canTradeWith(Money.of("-5"))).isFalse();
    }

    /** 고정 모드에서는 자산이 바닥나도 초기 자본으로 사이징하므로 자산 자체를 봐야 한다. */
    @Test
    void 고정_모드에서도_자산이_바닥나면_멈춘다() {
        assertThat(settings(CapitalMode.FIXED).canTradeWith(Money.of("-1"))).isFalse();
    }

    @Test
    void 초기_자본은_0보다_커야_하고_레버리지는_1_이상이어야_한다() {
        assertThatThrownBy(() -> new AccountSettings(Money.of("0"), RISK, 10, CapitalMode.FIXED))
                .isInstanceOf(InvalidBacktestException.class)
                .hasMessageContaining("초기 자본");
        assertThatThrownBy(() -> new AccountSettings(START, RISK, 0, CapitalMode.FIXED))
                .isInstanceOf(InvalidBacktestException.class)
                .hasMessageContaining("레버리지");
    }
}
