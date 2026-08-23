package com.coinwin.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ExchangeRateTest {

    private static final Instant AT = Instant.parse("2026-08-23T15:04:04Z");

    private static ExchangeRate 환율(String 원) {
        return new ExchangeRate(new BigDecimal(원), AT);
    }

    @Test
    void 금액에_환율을_곱해_원화를_낸다() {
        assertThat(환율("1370.00").convert(Money.of("800")).value())
                .isEqualByComparingTo("1096000");
    }

    /** 원에는 그 아래 단위가 없다. 1,968,265.30원 은 존재하지 않는 금액이다. */
    @Test
    void 원화는_소수점을_버리고_반올림한다() {
        assertThat(환율("1370.00").convert(Money.of("1436.69")).value())
                .isEqualByComparingTo("1968265");
    }

    @Test
    void 손실도_옮긴다_원화는_음수를_허용한다() {
        assertThat(환율("1370.00").convert(Money.of("-100.00")).value())
                .isEqualByComparingTo("-137000");
    }

    @Test
    void 환율은_스케일_2_로_정규화된다() {
        assertThat(환율("1370.123").wonPerUsdt()).isEqualByComparingTo("1370.12");
    }

    @Test
    void 환율이_0_이면_거부한다() {
        assertThatThrownBy(() -> 환율("0"))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("환율");
    }

    @Test
    void 환율이_음수면_거부한다() {
        assertThatThrownBy(() -> 환율("-1"))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void 잰_시각이_없으면_거부한다() {
        assertThatThrownBy(() -> new ExchangeRate(new BigDecimal("1370"), null))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("환율을 잰 시각");
    }

    @Test
    void 옮길_금액이_없으면_거부한다() {
        assertThatThrownBy(() -> 환율("1370").convert(null))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("금액");
    }
}
