package com.coinwin.readout.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.indicator.domain.VolumeProfile;
import com.coinwin.indicator.domain.VolumeProfileSettings;
import com.coinwin.market.domain.Candle;
import com.coinwin.market.domain.CandleSeries;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 매물대를 지금 가격 기준으로 읽는다.
 *
 * <p><b>셋을 함께 내는 이유가 이 테스트의 요점이다.</b> 위·아래만 내면 "매물대가 없다" 와
 * "지금 매물대 한가운데에 있다" 가 화면에서 같은 모양이 되는데, 뒤쪽은 <b>어느 쪽으로
 * 움직이든 물린 물량을 지나야 한다</b>는 정반대의 사실이다.
 */
class VolumeProfileReadoutTest {

    private static final Instant START = Instant.parse("2026-08-01T00:00:00Z");

    private static final VolumeProfileSettings SETTINGS =
            new VolumeProfileSettings(4, new BigDecimal("1.5"));

    private int sequence;

    @Test
    void POC_는_가장_두꺼운_칸의_가운데다() {
        VolumeProfileReadout readout = 판독(Price.of("190.00"));

        assertThat(readout.pointOfControl().value()).isEqualByComparingTo("112.50");
    }

    /** 대와 같은 규칙이다 — 아래에 있는 구간은 위쪽 모서리가 먼저 닿는다. */
    @Test
    void 아래_매물대는_위쪽_모서리가_가깝다() {
        VolumeProfileReadout readout = 판독(Price.of("190.00"));

        VolumeShelfReadout below = readout.below().orElseThrow();
        assertThat(below.near().value()).isEqualByComparingTo("150.00");
        assertThat(below.far().value()).isEqualByComparingTo("100.00");
        // (190 − 150) ÷ 190 = 21.0526%
        assertThat(below.distancePercent().value().doubleValue()).isEqualTo(21.0526);
        assertThat(readout.above()).isEmpty();
        assertThat(readout.here()).isEmpty();
    }

    @Test
    void 위_매물대는_아래쪽_모서리가_가깝다() {
        VolumeProfileReadout readout = 판독(Price.of("98.00"));

        VolumeShelfReadout above = readout.above().orElseThrow();
        assertThat(above.near().value()).isEqualByComparingTo("100.00");
        assertThat(above.far().value()).isEqualByComparingTo("150.00");
        assertThat(readout.below()).isEmpty();
    }

    /** 품고 있는 매물대는 위도 아래도 아니다. 그 사실 자체가 정보다. */
    @Test
    void 매물대_안에_있으면_위아래가_아니라_품은_것으로_나온다() {
        VolumeProfileReadout readout = 판독(Price.of("110.00"));

        assertThat(readout.here()).isPresent();
        assertThat(readout.below()).isEmpty();
        assertThat(readout.above()).isEmpty();
        assertThat(readout.here().orElseThrow().share().value().doubleValue())
                .isEqualTo(83.3333);
    }

    /**
     * 100~200 을 오간 40 이 네 칸에 10 씩, 100~150 을 오간 80 이 아래 두 칸에 40 씩 들어간다.
     * 아래 두 칸이 50·50 이라 평균 30 의 1.5배(45)를 넘고, 붙어 있으므로 한 덩이가 된다.
     */
    private VolumeProfileReadout 판독(Price close) {
        CandleSeries series = CandleSeries.of(봉(100, 200, "40"), 봉(100, 150, "80"));
        return VolumeProfileReadout.of(VolumeProfile.over(series, SETTINGS), close);
    }

    private Candle 봉(int low, int high, String volume) {
        Price close = Price.of(BigDecimal.valueOf(low));
        return new Candle(
                START.plus(Duration.ofHours(sequence++)),
                close,
                Price.of(BigDecimal.valueOf(high)),
                Price.of(BigDecimal.valueOf(low)),
                close,
                Quantity.of(new BigDecimal(volume)));
    }

    /** 목록이 비면 애초에 매물대를 만들 수 없다 — 그 경계는 {@code VolumeProfileTest} 가 지킨다. */
    @Test
    void 캔들이_있으면_언제나_POC_가_있다() {
        assertThat(List.of(판독(Price.of("110.00")), 판독(Price.of("190.00"))))
                .allSatisfy(readout -> assertThat(readout.pointOfControl()).isNotNull());
    }
}
