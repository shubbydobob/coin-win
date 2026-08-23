package com.coinwin.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class WonTest {

    @Test
    void 소수점_아래는_반올림해서_사라진다() {
        assertThat(Won.of(new BigDecimal("1968265.30")).value()).isEqualByComparingTo("1968265");
        assertThat(Won.of(new BigDecimal("1968265.50")).value()).isEqualByComparingTo("1968266");
    }

    /** HALF_UP 은 0 에서 멀어지는 쪽이다. 음수 경계가 갈리는 자리라 못 박아 둔다. */
    @Test
    void 음수도_같은_규칙으로_반올림한다() {
        assertThat(Won.of(new BigDecimal("-1370.50")).value()).isEqualByComparingTo("-1371");
    }

    @Test
    void 값이_없으면_거부한다() {
        assertThatThrownBy(() -> Won.of(null))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("원화 금액");
    }
}
