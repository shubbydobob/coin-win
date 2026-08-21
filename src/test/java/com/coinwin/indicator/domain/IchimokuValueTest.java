package com.coinwin.indicator.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Price;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IchimokuValueTest {

    /** 선행스팬 1 이 2 위에 있는 상승 구름. 구름 구간은 60300 ~ 60700. */
    private static final IchimokuValue BULLISH = new IchimokuValue(
            Price.of("60900"), Price.of("60800"), Price.of("60700"), Price.of("60300"),
            Optional.of(Price.of("61000")));

    @Test
    void 구름_위의_가격은_ABOVE_다() {
        assertThat(BULLISH.positionOf(Price.of("60701"))).isEqualTo(BandPosition.ABOVE);
    }

    @Test
    void 구름_안의_가격은_INSIDE_다() {
        assertThat(BULLISH.positionOf(Price.of("60500"))).isEqualTo(BandPosition.INSIDE);
    }

    @Test
    void 구름_아래의_가격은_BELOW_다() {
        assertThat(BULLISH.positionOf(Price.of("60299"))).isEqualTo(BandPosition.BELOW);
    }

    /**
     * 선행스팬 1 이 2 아래로 내려간 하락 구름에서도 위치 판정은 그대로 성립해야 한다.
     * 두 선의 순서를 가정하고 상단·하단을 고정하면 이 경우가 통째로 뒤집힌다.
     */
    @Test
    void 하락_구름에서도_큰_쪽이_상단이_된다() {
        IchimokuValue bearish = new IchimokuValue(
                Price.of("60900"), Price.of("60800"), Price.of("60300"), Price.of("60700"),
                Optional.empty());

        assertThat(bearish.cloud()).isEqualTo(BULLISH.cloud());
        assertThat(bearish.positionOf(Price.of("60500"))).isEqualTo(BandPosition.INSIDE);
        assertThat(bearish.bullishCloud()).isFalse();
    }

    @Test
    void 선행스팬_1이_2_위에_있으면_상승_구름이다() {
        assertThat(BULLISH.bullishCloud()).isTrue();
    }

    @Test
    void 구름은_두_선행스팬이_감싸는_구간이다() {
        assertThat(BULLISH.cloud())
                .isEqualTo(new PriceBand(Price.of("60700"), Price.of("60300")));
    }

    /** 후행스팬이 비어 있어도 나머지 넷은 유효하다 — 가장 최근 봉이 늘 그 상태다. */
    @Test
    void 후행스팬이_비어_있어도_값이_성립한다() {
        IchimokuValue latest = new IchimokuValue(
                Price.of("60900"), Price.of("60800"), Price.of("60700"), Price.of("60300"),
                Optional.empty());

        assertThat(latest.laggingSpan()).isEmpty();
        assertThat(latest.positionOf(Price.of("60500"))).isEqualTo(BandPosition.INSIDE);
    }

    @Test
    void 다섯_인자는_모두_null_일_수_없다() {
        Price price = Price.of("60000");
        Optional<Price> lagging = Optional.of(price);

        assertThatThrownBy(() -> new IchimokuValue(null, price, price, price, lagging))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new IchimokuValue(price, null, price, price, lagging))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new IchimokuValue(price, price, null, price, lagging))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new IchimokuValue(price, price, price, null, lagging))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new IchimokuValue(price, price, price, price, null))
                .isInstanceOf(InvalidValueException.class);
    }
}
