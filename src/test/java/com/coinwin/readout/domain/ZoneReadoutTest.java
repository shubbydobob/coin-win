package com.coinwin.readout.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.backtest.domain.PriceZone;
import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Price;
import com.coinwin.indicator.domain.PriceBand;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ZoneReadoutTest {

    private static PriceZone 대(String 아래, String 위, int 터치) {
        return new PriceZone(PriceBand.enclosing(가격(아래), 가격(위)), 터치);
    }

    private static Price 가격(String value) {
        return Price.of(new BigDecimal(value));
    }

    /**
     * <b>가까운 모서리는 이미 정해져 있다.</b> 지지대는 가격 아래에 있으므로 위쪽 모서리가
     * 먼저 닿고, 그 값이 판단의 기준이다. 방향을 인자로 받으면 부르는 쪽이 틀릴 수 있다.
     */
    @Test
    void 아래에_있는_대는_위쪽_모서리가_가깝다() {
        ZoneReadout 판독 = ZoneReadout.of(대("76120", "76500", 3), 가격("79000"));

        assertThat(판독.near()).isEqualTo(가격("76500"));
        assertThat(판독.far()).isEqualTo(가격("76120"));
    }

    @Test
    void 위에_있는_대는_아래쪽_모서리가_가깝다() {
        ZoneReadout 판독 = ZoneReadout.of(대("81000", "81400", 2), 가격("79000"));

        assertThat(판독.near()).isEqualTo(가격("81000"));
        assertThat(판독.far()).isEqualTo(가격("81400"));
    }

    /**
     * <b>거리는 도메인이 낸다.</b> 화면에서 (대 − 현재가) ÷ 현재가 를 하면 그 산술이
     * {@code docs/adr/020} 이 금지한 "프론트가 만든 수" 가 된다.
     */
    @Test
    void 거리는_가까운_모서리까지의_비율이다() {
        ZoneReadout 판독 = ZoneReadout.of(대("76120", "76500", 3), 가격("80000"));

        // (80000 − 76500) ÷ 80000 = 4.375%
        assertThat(판독.distancePercent().value()).isEqualByComparingTo(new BigDecimal("4.3750"));
    }

    /**
     * <b>거리에 부호를 싣지 않는다.</b> 위인지 아래인지는 이 값이 지지에 붙었는지 저항에
     * 붙었는지가 이미 말한다. 부호를 또 실으면 같은 사실이 두 곳에 생기고 한쪽만 뒤집힌다.
     */
    @Test
    void 거리는_언제나_0_이상이다() {
        ZoneReadout 아래 = ZoneReadout.of(대("76120", "76500", 3), 가격("79000"));
        ZoneReadout 위 = ZoneReadout.of(대("81000", "81400", 2), 가격("79000"));

        assertThat(아래.distancePercent().value()).isPositive();
        assertThat(위.distancePercent().value()).isPositive();
    }

    /** 대 안에 있으면 가까운 모서리는 아래쪽이다 — 위쪽 모서리가 가격보다 위이기 때문이다. */
    @Test
    void 대_안에_있으면_아래쪽_모서리를_가깝다고_본다() {
        ZoneReadout 판독 = ZoneReadout.of(대("78000", "79500", 4), 가격("79000"));

        assertThat(판독.near()).isEqualTo(가격("78000"));
        assertThat(판독.distancePercent().value()).isPositive();
    }

    @Test
    void 터치가_없는_대는_판독할_수_없다() {
        assertThatThrownBy(() -> new ZoneReadout(가격("1"), 가격("2"), 0, null))
                .isInstanceOf(InvalidValueException.class);
    }
}
