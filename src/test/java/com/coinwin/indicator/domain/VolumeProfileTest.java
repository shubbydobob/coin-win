package com.coinwin.indicator.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.domain.Candle;
import com.coinwin.market.domain.CandleSeries;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 매물대 — 어느 가격에서 얼마나 오갔는가.
 *
 * <p><b>기댓값을 손으로 풀 수 있는 캔들만 쓴다.</b> 지표 golden test 와 같은 태도다 — 실제
 * 캔들로 "그럴듯한 숫자" 를 확인하면 한 칸 밀린 구현도 통과한다({@code docs/adr/015}).
 */
class VolumeProfileTest {

    private static final Instant START = Instant.parse("2026-08-01T00:00:00Z");

    private static final VolumeProfileSettings SETTINGS = new VolumeProfileSettings(4, new BigDecimal("1.5"));

    /**
     * 봉 하나가 100~200 을 오가며 40 을 거래했고 칸이 넷이면, 네 칸이 각각 10 씩이다.
     *
     * <p>이 테스트가 고정하는 것은 <b>"고가와 저가 사이에 고르게 나눈다"</b> 는 정의다.
     * 종가에 몰아넣는 구현이었다면 한 칸이 40, 나머지가 0 이 된다.
     */
    @Test
    void 한_봉의_거래량은_고가와_저가_사이에_고르게_나뉜다() {
        VolumeProfile profile = VolumeProfile.over(
                CandleSeries.of(봉(100, 200, 150, "40")), SETTINGS);

        assertThat(profile.bins()).hasSize(4);
        profile.bins().forEach(bin ->
                assertThat(bin.volume().value()).isEqualByComparingTo("10"));
        assertThat(profile.totalVolume().value()).isEqualByComparingTo("40");
    }

    @Test
    void 고가와_저가가_같은_봉은_한_칸에_통째로_들어간다() {
        VolumeProfile profile = VolumeProfile.over(
                CandleSeries.of(봉(100, 200, 150, "40"), 봉(100, 100, 100, "8")), SETTINGS);

        assertThat(profile.bins().getFirst().volume().value()).isEqualByComparingTo("18");
        assertThat(profile.totalVolume().value()).isEqualByComparingTo("48");
    }

    /** 나눠 담아도 총량은 보존된다. 반올림 먼지 이상으로 새면 그것이 곧 버그다. */
    @Test
    void 칸들의_합은_캔들_거래량의_합과_같다() {
        VolumeProfile profile = VolumeProfile.over(들쭉날쭉한_캔들(), SETTINGS);

        assertThat(profile.totalVolume().value().doubleValue())
                .isCloseTo(60.0, within(0.0001));
    }

    /**
     * 가장 두꺼운 칸이 POC 다.
     *
     * <p>아래쪽 절반에만 놓인 봉을 하나 더하면 아래 두 칸이 두꺼워진다.
     */
    @Test
    void 가장_두꺼운_칸이_POC_다() {
        VolumeProfile profile = VolumeProfile.over(
                CandleSeries.of(봉(100, 200, 150, "40"), 봉(100, 125, 110, "40")), SETTINGS);

        assertThat(profile.pointOfControl().band().lower().value())
                .isEqualByComparingTo("100.00");
        assertThat(profile.pointOfControl().volume().value()).isEqualByComparingTo("50");
    }

    /** 두꺼운 칸이 나란히 있으면 매물대 둘이 아니라 폭이 넓은 하나다. */
    @Test
    void 붙어_있는_두꺼운_칸들은_한_덩이가_된다() {
        VolumeProfile profile = VolumeProfile.over(
                CandleSeries.of(봉(100, 200, 150, "40"), 봉(100, 150, 120, "80")), SETTINGS);

        List<VolumeShelf> shelves = profile.shelves();

        assertThat(shelves).hasSize(1);
        assertThat(shelves.getFirst().band().lower().value()).isEqualByComparingTo("100.00");
        assertThat(shelves.getFirst().band().upper().value()).isEqualByComparingTo("150.00");
    }

    /**
     * 100~200 을 오간 40 은 네 칸에 10 씩, 100~150 을 오간 80 은 아래 두 칸에 40 씩 들어간다.
     * 아래 두 칸이 50·50 이고 전체가 120 이므로 한 덩이가 된 매물대의 비중은 100/120 이다.
     */
    @Test
    void 비중은_전체_거래량_대비다() {
        VolumeProfile profile = VolumeProfile.over(
                CandleSeries.of(봉(100, 200, 150, "40"), 봉(100, 150, 120, "80")), SETTINGS);

        assertThat(shelfShare(profile)).isCloseTo(83.3333, within(0.001));
    }

    @Test
    void 위와_아래에서_가장_가까운_매물대를_고른다() {
        VolumeProfile profile = VolumeProfile.over(양쪽에_매물대가_있는_캔들(), SETTINGS);

        assertThat(profile.nearestBelow(Price.of("150.00"))).isPresent();
        assertThat(profile.nearestAbove(Price.of("150.00"))).isPresent();
        assertThat(profile.nearestBelow(Price.of("150.00")).orElseThrow().band().upper().value())
                .isLessThanOrEqualTo(new BigDecimal("150.00"));
        assertThat(profile.nearestAbove(Price.of("150.00")).orElseThrow().band().lower().value())
                .isGreaterThanOrEqualTo(new BigDecimal("150.00"));
    }

    /** 매물대 한가운데에 있는 것과 매물대가 없는 것은 다른 사실이다. */
    @Test
    void 가격을_품은_매물대는_위도_아래도_아니다() {
        VolumeProfile profile = VolumeProfile.over(
                CandleSeries.of(봉(100, 200, 150, "40"), 봉(100, 150, 120, "80")), SETTINGS);
        Price 안쪽 = Price.of("125.00");

        assertThat(profile.containing(안쪽)).isPresent();
        assertThat(profile.nearestBelow(안쪽)).isEmpty();
        assertThat(profile.nearestAbove(안쪽)).isEmpty();
    }

    @Test
    void 캔들이_없으면_매물대를_만들_수_없다() {
        assertThatThrownBy(() -> VolumeProfile.over(CandleSeries.empty(), SETTINGS))
                .isInstanceOf(InsufficientCandlesException.class);
    }

    @Test
    void 배수가_1_이하면_설정이_아니다() {
        assertThatThrownBy(() -> new VolumeProfileSettings(4, BigDecimal.ONE))
                .isInstanceOf(RuntimeException.class);
    }

    private static double shelfShare(VolumeProfile profile) {
        return profile.shelves().getFirst().share().value().doubleValue();
    }

    private CandleSeries 양쪽에_매물대가_있는_캔들() {
        return CandleSeries.of(
                봉(100, 200, 150, "20"),
                봉(100, 125, 110, "40"),
                봉(175, 200, 190, "40"));
    }

    private CandleSeries 들쭉날쭉한_캔들() {
        List<Candle> candles = new ArrayList<>();
        candles.add(봉(100, 180, 140, "20"));
        candles.add(봉(120, 200, 160, "25"));
        candles.add(봉(110, 150, 130, "15"));
        return new CandleSeries(candles);
    }

    /** 봉마다 한 시간씩 뒤로 민다. 캔들은 시간 오름차순이고 같은 시각이 두 번 오면 안 된다. */
    private int sequence;

    private Candle 봉(int low, int high, int close, String volume) {
        return new Candle(
                START.plus(Duration.ofHours(sequence++)),
                Price.of(BigDecimal.valueOf(close)),
                Price.of(BigDecimal.valueOf(high)),
                Price.of(BigDecimal.valueOf(low)),
                Price.of(BigDecimal.valueOf(close)),
                Quantity.of(new BigDecimal(volume)));
    }
}
