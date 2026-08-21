package com.coinwin.indicator.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import com.coinwin.indicator.IndicatorFixtures;
import com.coinwin.market.domain.CandleSeries;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * 볼린저 밴드 golden test.
 *
 * <p>기댓값은 구현이 아니라 <b>등차수열의 모집단 분산 공식</b>에서 나온다. 종가가 공차 d 인
 * 등차수열 n 개일 때 분산은 {@code d² (n² − 1) / 12} 다. n=20, d=100 이면 332,500 이고
 * σ = √332500 = 576.6281297335…, 2σ = 1153.2562594670… — 값이 구현과 독립적으로 유도된다.
 */
class BollingerBandsTest {

    private static final BollingerBands STANDARD = BollingerBands.standard();

    @Test
    void 트레이딩뷰_기본값은_20봉과_2배다() {
        assertThat(STANDARD.period()).isEqualTo(20);
        assertThat(STANDARD.multiplier()).isEqualByComparingTo("2");
    }

    /**
     * 종가 60000 부터 100 씩 오르는 20봉. 평균 60950, 2σ = 1153.25625946…
     * 상단 62103.25625… → 62103.26, 하단 59796.74374… → 59796.74.
     */
    @Test
    void 등차수열_20봉의_밴드는_손계산_값과_센트까지_일치한다() {
        List<IndicatorPoint<BollingerValue>> points =
                STANDARD.over(IndicatorFixtures.rising(20, 100, 50));

        assertThat(points).hasSize(1);
        assertThat(points.getFirst().at()).isEqualTo(IndicatorFixtures.hour(19));
        assertThat(points.getFirst().value()).isEqualTo(new BollingerValue(
                Price.of("62103.26"), Price.of("60950.00"), Price.of("59796.74")));
    }

    @Test
    void 등차수열_20봉의_밴드폭은_3_7843_퍼센트다() {
        BollingerValue value = STANDARD.over(IndicatorFixtures.rising(20, 100, 50))
                .getFirst().value();

        // (62103.26 − 59796.74) / 60950 × 100 = 3.784282…
        assertThat(value.bandWidth()).isEqualTo(Percentage.of("3.7843"));
    }

    /**
     * 창이 밀려도 같은 공차면 표준편차가 같다. 평균만 이동하므로 밴드가 통째로 평행이동한다.
     * 창을 자르는 인덱스가 어긋나면 이 성질이 먼저 깨진다.
     */
    @Test
    void 창이_밀리면_평균만_이동하고_폭은_그대로다() {
        List<IndicatorPoint<BollingerValue>> points =
                STANDARD.over(IndicatorFixtures.rising(25, 100, 50));

        assertThat(points).hasSize(6);
        assertThat(points.getLast().at()).isEqualTo(IndicatorFixtures.hour(24));
        assertThat(points.getLast().value()).isEqualTo(new BollingerValue(
                Price.of("62603.26"), Price.of("61450.00"), Price.of("60296.74")));
    }

    @Test
    void 종가가_일정하면_표준편차가_0이라_세_선이_겹치고_밴드폭도_0이다() {
        BollingerValue value = STANDARD.over(IndicatorFixtures.rising(20, 0, 50))
                .getFirst().value();

        assertThat(value.upper()).isEqualTo(Price.of("60000"));
        assertThat(value.middle()).isEqualTo(Price.of("60000"));
        assertThat(value.lower()).isEqualTo(Price.of("60000"));
        assertThat(value.bandWidth()).isEqualTo(Percentage.of("0"));
    }

    @Test
    void 배수를_1로_주면_밴드가_절반_폭이_된다() {
        BollingerValue value = new BollingerBands(20, BigDecimal.ONE)
                .over(IndicatorFixtures.rising(20, 100, 50)).getFirst().value();

        // 60950 ± 576.6281297… → 61526.62813 / 60373.37187
        assertThat(value.upper()).isEqualTo(Price.of("61526.63"));
        assertThat(value.lower()).isEqualTo(Price.of("60373.37"));
    }

    /**
     * 이중 반올림 회귀 검사. 평균이 60000.005 인 20봉에서, 중심선을 스케일 2 로 스냅한 뒤
     * 편차를 빼면 하단이 59999.97 이 된다. 스냅하지 않은 평균에서 계산하면 59999.96 이다.
     * <b>이 한 센트가 두 방식을 가른다.</b>
     */
    @Test
    void 상하단은_스냅하지_않은_평균에서_계산한다() {
        CandleSeries series = new CandleSeries(IntStream.range(0, 20)
                .mapToObj(i -> IndicatorFixtures.candle(
                        i, "60001", "59999", i == 19 ? "60000.10" : "60000.00"))
                .toList());

        BollingerValue value = STANDARD.over(series).getFirst().value();

        assertThat(value.middle()).isEqualTo(Price.of("60000.01"));
        assertThat(value.upper()).isEqualTo(Price.of("60000.05"));
        assertThat(value.lower()).isEqualTo(Price.of("59999.96"));
    }

    @Test
    void 캔들이_기간보다_적으면_거부한다() {
        assertThatThrownBy(() -> STANDARD.over(IndicatorFixtures.rising(19, 100, 50)))
                .isInstanceOf(InsufficientCandlesException.class)
                .hasMessageContaining("20")
                .hasMessageContaining("19");
    }

    @Test
    void 캔들이_정확히_기간만큼이면_값이_하나_나온다() {
        assertThat(STANDARD.over(IndicatorFixtures.rising(20, 100, 50))).hasSize(1);
    }

    @Test
    void 빈_묶음도_부족으로_거부한다() {
        assertThatThrownBy(() -> STANDARD.over(CandleSeries.empty()))
                .isInstanceOf(InsufficientCandlesException.class);
    }

    @Test
    void 기간이_2_미만이면_평균이_성립하지_않는다() {
        assertThatThrownBy(() -> new BollingerBands(1, BigDecimal.TWO))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void 표준편차_배수는_0보다_커야_한다() {
        assertThatThrownBy(() -> new BollingerBands(20, BigDecimal.ZERO))
                .isInstanceOf(InvalidIndicatorException.class)
                .hasMessageContaining("배수");
        assertThatThrownBy(() -> new BollingerBands(20, new BigDecimal("-1")))
                .isInstanceOf(InvalidIndicatorException.class);
    }

    @Test
    void 배수와_캔들_묶음은_null_일_수_없다() {
        assertThatThrownBy(() -> new BollingerBands(20, null))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> STANDARD.over(null))
                .isInstanceOf(InvalidValueException.class);
    }
}
