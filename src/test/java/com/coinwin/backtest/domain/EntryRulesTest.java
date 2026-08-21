package com.coinwin.backtest.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class EntryRulesTest {

    private static final BigDecimal ONE = BigDecimal.ONE;

    /** 버퍼 0 은 허용한다 — 손절을 대 원단에 정확히 붙이는 설정도 비교 대상이다. */
    @Test
    void 손절_버퍼는_0이어도_된다() {
        assertThatCode(() -> new EntryRules(BigDecimal.ZERO, ONE, false)).doesNotThrowAnyException();
    }

    @Test
    void 손절_버퍼는_음수일_수_없다() {
        assertThatThrownBy(() -> new EntryRules(new BigDecimal("-0.1"), ONE, false))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("버퍼");
    }

    /** 0 이면 모든 계획이 통과해 문턱이 없는 것과 같고, 음수는 뜻이 없다. */
    @Test
    void 최소_손익비는_0보다_커야_한다() {
        assertThatThrownBy(() -> new EntryRules(ONE, BigDecimal.ZERO, false))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("손익비");
        assertThatThrownBy(() -> new EntryRules(ONE, new BigDecimal("-1"), false))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void null은_거부된다() {
        assertThatThrownBy(() -> new EntryRules(null, ONE, false))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new EntryRules(ONE, null, false))
                .isInstanceOf(InvalidValueException.class);
    }
}
