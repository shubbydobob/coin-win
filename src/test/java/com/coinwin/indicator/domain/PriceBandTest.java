package com.coinwin.indicator.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import org.junit.jupiter.api.Test;

/**
 * 두 경계 사이 가격의 위치. 일목 구름과 볼린저 밴드가 <b>같은 판정</b>을 공유한다.
 *
 * <p>구름과 밴드에 각각 위치 enum 을 두면 같은 비교가 두 벌이 되고, 경계 포함 규칙이 한쪽만
 * 바뀌는 순간 두 지표가 다른 답을 낸다. conventions.md: 두 번째 나타나면 즉시 추출한다.
 */
class PriceBandTest {

    private static final PriceBand BAND =
            new PriceBand(Price.of("61000"), Price.of("60000"));

    @Test
    void 상단보다_높은_가격은_ABOVE_다() {
        assertThat(BAND.positionOf(Price.of("61000.01"))).isEqualTo(BandPosition.ABOVE);
    }

    @Test
    void 하단보다_낮은_가격은_BELOW_다() {
        assertThat(BAND.positionOf(Price.of("59999.99"))).isEqualTo(BandPosition.BELOW);
    }

    @Test
    void 두_경계_사이의_가격은_INSIDE_다() {
        assertThat(BAND.positionOf(Price.of("60500"))).isEqualTo(BandPosition.INSIDE);
    }

    /**
     * 경계는 밴드에 속한다. 돌파 판정은 "경계를 넘었는가" 이지 "경계에 닿았는가" 가 아니다.
     * 닿은 것을 돌파로 치면 종가가 상단에 정확히 걸린 캔들이 매번 신호를 만든다.
     */
    @Test
    void 상단과_정확히_같은_가격은_INSIDE_다() {
        assertThat(BAND.positionOf(Price.of("61000"))).isEqualTo(BandPosition.INSIDE);
    }

    @Test
    void 하단과_정확히_같은_가격은_INSIDE_다() {
        assertThat(BAND.positionOf(Price.of("60000"))).isEqualTo(BandPosition.INSIDE);
    }

    @Test
    void 상단과_하단이_같은_납작한_밴드도_성립한다() {
        PriceBand flat = new PriceBand(Price.of("60000"), Price.of("60000"));

        assertThat(flat.positionOf(Price.of("60000"))).isEqualTo(BandPosition.INSIDE);
        assertThat(flat.width()).isEqualTo(Money.of("0"));
    }

    @Test
    void 상단이_하단보다_낮으면_밴드가_아니다() {
        assertThatThrownBy(() -> new PriceBand(Price.of("60000"), Price.of("61000")))
                .isInstanceOf(InvalidIndicatorException.class)
                .hasMessageContaining("상단");
    }

    /**
     * 일목 선행스팬 1·2 는 어느 쪽이 위인지 정해져 있지 않다. 구름이 뒤집히는 것이 추세 전환
     * 신호이므로, 호출부마다 큰 쪽을 고르게 하면 그 비교가 흩어진다.
     */
    @Test
    void enclosing은_인자_순서와_무관하게_큰_쪽을_상단으로_삼는다() {
        PriceBand ascending = PriceBand.enclosing(Price.of("60000"), Price.of("61000"));
        PriceBand descending = PriceBand.enclosing(Price.of("61000"), Price.of("60000"));

        assertThat(ascending).isEqualTo(descending);
        assertThat(ascending.upper()).isEqualTo(Price.of("61000"));
        assertThat(ascending.lower()).isEqualTo(Price.of("60000"));
    }

    @Test
    void 밴드의_폭은_두_경계의_간격이다() {
        assertThat(BAND.width()).isEqualTo(Money.of("1000"));
    }

    @Test
    void 경계는_null_일_수_없다() {
        assertThatThrownBy(() -> new PriceBand(null, Price.of("60000")))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new PriceBand(Price.of("61000"), null))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> PriceBand.enclosing(null, Price.of("60000")))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void 위치를_판정할_가격은_null_일_수_없다() {
        assertThatThrownBy(() -> BAND.positionOf(null))
                .isInstanceOf(InvalidValueException.class);
    }
}
