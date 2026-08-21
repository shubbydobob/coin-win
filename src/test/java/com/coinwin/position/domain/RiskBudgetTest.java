package com.coinwin.position.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import org.junit.jupiter.api.Test;

class RiskBudgetTest {

    @Test
    void 리스크_금액은_잔고에_비율을_적용한_값이다() {
        assertThat(RiskBudget.of("800", "2").riskAmount()).isEqualTo(Money.of("16.00"));
    }

    @Test
    void 리스크_금액이_잔고를_초과하면_거부된다() {
        assertThatThrownBy(() -> RiskBudget.of("800", "101"))
                .isInstanceOf(RiskExceedsBalanceException.class)
                .hasMessageContaining("808.00")
                .hasMessageContaining("800.00");
    }

    @Test
    void 잔고_전액을_거는_것은_허용된다() {
        assertThatCode(() -> RiskBudget.of("800", "100")).doesNotThrowAnyException();
    }

    @Test
    void 잔고가_0이면_거부된다() {
        assertThatThrownBy(() -> RiskBudget.of("0", "2"))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("0 보다 커야");
    }

    @Test
    void 잔고나_비율이_없으면_거부된다() {
        assertThatThrownBy(() -> new RiskBudget(null, Percentage.of("2")))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("계좌 잔고");
        assertThatThrownBy(() -> new RiskBudget(Money.of("800"), null))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("리스크 비율");
    }
}
