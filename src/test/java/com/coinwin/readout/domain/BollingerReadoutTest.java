package com.coinwin.readout.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import com.coinwin.indicator.domain.BandPosition;
import com.coinwin.indicator.domain.BandRatio;
import com.coinwin.indicator.domain.BollingerValue;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 볼린저 판독 — 「밴드 안」 이 접어 버리던 것.
 *
 * <p>하단에 붙어 있는 것과 상단 바로 아래인 것은 같은 「안」 인데 되돌림을 보는 사람에게는
 * 정반대 자리다. 근거는 {@code docs/spec/indicator-usage.md} § 4.2.
 */
class BollingerReadoutTest {

    @Test
    @DisplayName("밴드 안 위치는 하단 0 · 상단 1 로 잰다")
    void 밴드_안_어디() {
        assertThat(비율(60500)).isEqualByComparingTo("0.7500");
        assertThat(비율(59500)).isEqualByComparingTo("0.2500");
    }

    /** 밖으로 나간 정도를 자르지 않는다. 자르면 닿은 것과 뚫은 것이 같은 값이 된다. */
    @Test
    @DisplayName("밴드 밖은 0~1 을 벗어난다")
    void 밖으로_나가면() {
        assertThat(비율(62000)).isEqualByComparingTo("1.5000");
        assertThat(판독(62000).position()).isEqualTo(BandPosition.ABOVE);
    }

    /**
     * <b>폭이 0 이면 비율만 빠진다.</b> 나머지 값은 그대로 성립하므로 판독 전체를 세우지
     * 않는다 — 0 이나 0.5 로 채우면 그것이 없는 사실이 된다.
     */
    @Test
    @DisplayName("폭이 0 이면 밴드 안 위치가 비어 있다")
    void 폭이_없으면() {
        BollingerReadout 판독 = BollingerReadout.of(
                new BollingerValue(가격(60000), 가격(60000), 가격(60000)),
                가격(60000),
                Percentage.of(BigDecimal.ZERO),
                0);

        assertThat(판독.ratio()).isEmpty();
        assertThat(판독.position()).isEqualTo(BandPosition.INSIDE);
        assertThat(판독.bandWidthPercent().value()).isEqualByComparingTo("0.0000");
    }

    @Test
    @DisplayName("밴드 폭은 중심선 대비 비율이다")
    void 밴드_폭() {
        assertThat(판독(60000).bandWidthPercent().value()).isEqualByComparingTo("3.3333");
    }

    private static BigDecimal 비율(int close) {
        return 판독(close).ratio().map(BandRatio::value).orElseThrow();
    }

    private static BollingerReadout 판독(int close) {
        return BollingerReadout.of(
                new BollingerValue(가격(61000), 가격(60000), 가격(59000)),
                가격(close),
                Percentage.of(BigDecimal.ZERO),
                0);
    }

    private static Price 가격(int value) {
        return Price.of(BigDecimal.valueOf(value));
    }
}
