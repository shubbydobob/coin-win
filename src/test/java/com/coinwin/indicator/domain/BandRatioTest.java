package com.coinwin.indicator.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.common.domain.Price;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 밴드 안에서 어디쯤인가.
 *
 * <p><b>{@link BandPosition} 이 답하지 못하는 것을 잰다.</b> 「밴드 안」 은 하단에 붙어 있는
 * 것과 상단 바로 아래인 것을 같은 사실로 만든다.
 */
class BandRatioTest {

    private static final PriceBand BAND = band("60000", "58000");

    @Test
    @DisplayName("하단이 0, 상단이 1 이다")
    void 경계() {
        assertThat(비율("58000")).isEqualByComparingTo("0.0000");
        assertThat(비율("60000")).isEqualByComparingTo("1.0000");
        assertThat(비율("59000")).isEqualByComparingTo("0.5000");
    }

    /**
     * <b>0~1 로 자르지 않는다.</b> 얼마나 벗어났는지가 밴드를 보는 이유의 절반이고, 자르면
     * 상단에 닿은 것과 한참 위로 뚫고 나간 것이 같은 값이 된다.
     */
    @Test
    @DisplayName("밴드 밖은 0 아래이거나 1 위다")
    void 밖으로_나가면() {
        assertThat(비율("61000")).isEqualByComparingTo("1.5000");
        assertThat(비율("57000")).isEqualByComparingTo("-0.5000");
    }

    /**
     * <b>폭이 0 이면 물음 자체가 성립하지 않는다.</b> 0 이나 0.5 로 적으면 그것이 없는
     * 사실이 된다 — 「말할 수 없는 것을 0 으로 적지 않는다」와 같은 자리다.
     */
    @Test
    @DisplayName("폭이 0 이면 비어 있다")
    void 폭이_없으면() {
        assertThat(band("60000", "60000").ratioOf(Price.of(new BigDecimal("60000")))).isEmpty();
    }

    private static BigDecimal 비율(String price) {
        return BAND.ratioOf(Price.of(new BigDecimal(price))).orElseThrow().value();
    }

    private static PriceBand band(String upper, String lower) {
        return new PriceBand(Price.of(new BigDecimal(upper)), Price.of(new BigDecimal(lower)));
    }
}
