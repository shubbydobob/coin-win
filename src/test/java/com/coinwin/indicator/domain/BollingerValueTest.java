package com.coinwin.indicator.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import org.junit.jupiter.api.Test;

class BollingerValueTest {

    private static final BollingerValue VALUE = new BollingerValue(
            Price.of("62103.26"), Price.of("60950"), Price.of("59796.74"));

    @Test
    void 밴드폭은_상하단_간격을_중심선으로_나눈_비율이다() {
        // (62103.26 - 59796.74) / 60950 × 100 = 3.784282…%
        assertThat(VALUE.bandWidth()).isEqualTo(Percentage.of("3.7843"));
    }

    @Test
    void 상단_위의_가격은_ABOVE_다() {
        assertThat(VALUE.positionOf(Price.of("62200"))).isEqualTo(BandPosition.ABOVE);
    }

    @Test
    void 하단_아래의_가격은_BELOW_다() {
        assertThat(VALUE.positionOf(Price.of("59000"))).isEqualTo(BandPosition.BELOW);
    }

    @Test
    void 밴드_안의_가격은_INSIDE_다() {
        assertThat(VALUE.positionOf(Price.of("60950"))).isEqualTo(BandPosition.INSIDE);
    }

    @Test
    void 밴드는_상단과_하단으로_이루어진다() {
        assertThat(VALUE.band()).isEqualTo(new PriceBand(VALUE.upper(), VALUE.lower()));
    }

    /**
     * 중심선은 정의상 상하단 사이에 있다. 벗어난 값이 만들어졌다면 셋 중 하나가 다른 구간에서
     * 계산된 것이고, 그것은 밴드폭과 위치 판정을 동시에 조용히 틀리게 만든다.
     */
    @Test
    void 중심선이_밴드_밖이면_거부한다() {
        assertThatThrownBy(() -> new BollingerValue(
                Price.of("62000"), Price.of("63000"), Price.of("60000")))
                .isInstanceOf(InvalidIndicatorException.class)
                .hasMessageContaining("중심선");
    }

    @Test
    void 상단이_하단보다_낮으면_거부한다() {
        assertThatThrownBy(() -> new BollingerValue(
                Price.of("59000"), Price.of("60000"), Price.of("61000")))
                .isInstanceOf(InvalidIndicatorException.class);
    }

    @Test
    void 세_선은_null_일_수_없다() {
        assertThatThrownBy(() -> new BollingerValue(null, Price.of("1"), Price.of("1")))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new BollingerValue(Price.of("1"), null, Price.of("1")))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new BollingerValue(Price.of("1"), Price.of("1"), null))
                .isInstanceOf(InvalidValueException.class);
    }
}
