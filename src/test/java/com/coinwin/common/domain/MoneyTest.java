package com.coinwin.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void 스케일이_다른_같은_값은_동등하다() {
        assertThat(Money.of("800.5")).isEqualTo(Money.of("800.50"));
    }

    @Test
    void 소수점_셋째자리는_HALF_UP으로_반올림된다() {
        assertThat(Money.of("800.005").value()).isEqualByComparingTo("800.01");
        assertThat(Money.of("800.004").value()).isEqualByComparingTo("800.00");
    }

    @Test
    void 스케일은_항상_2로_유지된다() {
        assertThat(Money.of("800").value().scale()).isEqualTo(2);
    }

    /**
     * Price / Quantity / Percentage 와 달리 Money 만 음수를 허용한다.
     * 실현손익(TradeRecord.realizedPnl)이 음수여야 하기 때문이다.
     */
    @Test
    void 실현손익을_표현해야_하므로_음수가_허용된다() {
        assertThat(Money.of("-120.50").value()).isEqualByComparingTo("-120.50");
    }

    @Test
    void null은_거부된다() {
        assertThatThrownBy(() -> Money.of((BigDecimal) null))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void 금액을_단가로_나누면_수량이_된다() {
        Money riskAmount = Money.of("40");
        Money perUnitLoss = Money.of("1000");

        assertThat(riskAmount.dividedBy(perUnitLoss)).isEqualTo(Quantity.of("0.04"));
    }

    @Test
    void 나눗셈_결과는_수량_스케일_8자리에서_HALF_UP으로_반올림된다() {
        // 1 / 3 = 0.333... → 8자리
        assertThat(Money.of("1").dividedBy(Money.of("3")))
                .isEqualTo(Quantity.of("0.33333333"));
    }

    @Test
    void 영으로_나누면_도메인_예외를_던진다() {
        assertThatThrownBy(() -> Money.of("40").dividedBy(Money.of("0")))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("0");
    }

    @Test
    void 크기_비교는_스케일이_아니라_값으로_한다() {
        assertThat(Money.of("959.20").isGreaterThan(Money.of("800"))).isTrue();
        assertThat(Money.of("800.0").isGreaterThan(Money.of("800.00"))).isFalse();
        assertThat(Money.of("-1").isGreaterThan(Money.of("0"))).isFalse();
    }

    @Test
    void 무차원_배수를_곱하면_금액_스케일에서_반올림된다() {
        // 복리 자산 곡선의 한 점: 초기 자본 800 에 누적 배수 1.043 을 적용
        assertThat(Money.of("800").times(new BigDecimal("1.043"))).isEqualTo(Money.of("834.40"));
        assertThat(Money.of("800").times(new BigDecimal("1.0430625")))
                .isEqualTo(Money.of("834.45"));
    }

    @Test
    void 배수가_null이면_거부된다() {
        assertThatThrownBy(() -> Money.of("800").times(null))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("배수");
    }

    @Test
    void 금액의_차는_음수가_될_수_있다() {
        assertThat(Money.of("800").minus(Money.of("950"))).isEqualTo(Money.of("-150"));
        assertThat(Money.of("950").minus(Money.of("800"))).isEqualTo(Money.of("150"));
    }

    @Test
    void 전체_대비_비율은_비율_스케일_4자리에서_나온다() {
        // 고점 950 에서 800 으로 밀린 낙폭: 150 / 950 = 15.78947...%
        assertThat(Money.of("150").percentOf(Money.of("950")))
                .isEqualTo(Percentage.of("15.7895"));
    }

    @Test
    void 전체가_0이면_비율은_성립하지_않는다() {
        assertThatThrownBy(() -> Money.of("150").percentOf(Money.of("0")))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("0");
    }

    /** 낙폭은 고점 대비 하락분이므로 음수가 나올 수 없다. 나왔다면 고점 추적이 틀린 것이다. */
    @Test
    void 음수_금액의_비율은_거부된다() {
        assertThatThrownBy(() -> Money.of("-1").percentOf(Money.of("950")))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("음수");
    }
}
