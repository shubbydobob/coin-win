package com.coinwin.indicator.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Price;
import com.coinwin.indicator.IndicatorFixtures;
import com.coinwin.market.domain.CandleSeries;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 일목균형표 golden test.
 *
 * <p>기댓값의 출처가 둘이다.
 *
 * <ul>
 *   <li><b>단조 증가 시리즈</b> — {@code high[i] = 60000 + 10i + 5}, {@code low[i] = …− 5} 이면
 *       어느 구간이든 최고가가 마지막, 최저가가 첫 캔들에 있으므로
 *       {@code midpoint(i, p) = 60000 + 10i − 5(p−1)} 이라는 닫힌 식이 나온다. 표준 설정
 *       9/26/52/26 을 이 식으로 검사한다.
 *   <li><b>비단조 13봉</b> — 극값이 구간 <b>중간</b>에 오도록 짠 캔들. 단조 입력만으로는
 *       "언제나 마지막 고가를 쓴다" 는 버그가 통과한다. 작은 설정 3/5/7/3 으로 손계산한다.
 * </ul>
 */
class IchimokuCloudTest {

    private static final IchimokuCloud STANDARD = IchimokuCloud.standard();
    private static final IchimokuCloud SMALL = new IchimokuCloud(3, 5, 7, 3);

    @Test
    void 트레이딩뷰_기본값은_9_26_52와_변위_26이다() {
        assertThat(STANDARD).isEqualTo(new IchimokuCloud(9, 26, 52, 26));
    }

    // ---------------------------------------------------------------------
    // 단조 증가 110봉 — midpoint(i, p) = 60000 + 10i − 5(p−1)
    // ---------------------------------------------------------------------

    /**
     * index 77 이 첫 값이다. 구름은 index 51 에서 계산된다.
     * 전환선 60000+770−40, 기준선 60000+770−125,
     * 선행 1 = (60470+60385)/2, 선행 2 = 60000+510−255, 후행 = close[103].
     */
    @Test
    void 단조증가_110봉의_첫_값은_손계산과_일치한다() {
        List<IndicatorPoint<IchimokuValue>> points =
                STANDARD.over(IndicatorFixtures.rising(110, 10, 5));

        assertThat(points).hasSize(33);
        assertThat(points.getFirst().at()).isEqualTo(IndicatorFixtures.hour(77));
        assertThat(points.getFirst().value()).isEqualTo(new IchimokuValue(
                Price.of("60730"), Price.of("60645"),
                Price.of("60427.50"), Price.of("60255"),
                Optional.of(Price.of("61030"))));
    }

    /** index 109. 구름은 index 83 에서 계산되고, 후행스팬은 아직 없다. */
    @Test
    void 단조증가_110봉의_마지막_값도_손계산과_일치한다() {
        IndicatorPoint<IchimokuValue> last =
                STANDARD.over(IndicatorFixtures.rising(110, 10, 5)).getLast();

        assertThat(last.at()).isEqualTo(IndicatorFixtures.hour(109));
        assertThat(last.value()).isEqualTo(new IchimokuValue(
                Price.of("61050"), Price.of("60965"),
                Price.of("60747.50"), Price.of("60575"),
                Optional.empty()));
    }

    /**
     * 후행스팬은 변위만큼 뒤의 캔들이 있어야 확정된다. 110봉이면 index 83 까지만 존재하고
     * 나머지 26개는 비어 있다 — 그 26개에도 구름 판정은 정상으로 나와야 한다.
     */
    @Test
    void 마지막_26봉의_후행스팬은_비어_있고_구름_판정은_살아_있다() {
        List<IndicatorPoint<IchimokuValue>> points =
                STANDARD.over(IndicatorFixtures.rising(110, 10, 5));

        assertThat(points.stream().filter(p -> p.value().laggingSpan().isPresent())).hasSize(7);
        assertThat(points.stream().filter(p -> p.value().laggingSpan().isEmpty())).hasSize(26);
        assertThat(points.getLast().value().positionOf(Price.of("61090")))
                .isEqualTo(BandPosition.ABOVE);
    }

    // ---------------------------------------------------------------------
    // 비단조 13봉 — 극값이 구간 중간에 온다
    // ---------------------------------------------------------------------

    /**
     * index 9, 설정 3/5/7/3. 구름은 index 6 에서 계산된다.
     *
     * <p>전환선: 고가 max(150,135,160)=160, 저가 min(120,115,130)=115 → 137.5
     * <br>기준선: 고가 160, 저가 min(105,90,120,115,130)=90 → 125
     * <br>선행 1: index 6 의 전환 110 과 기준 110 의 평균 → 110
     * <br>선행 2: index 0~6 의 고가 200(<b>구간 첫 캔들</b>), 저가 80 → 140
     */
    @Test
    void 극값이_구간_중간에_있어도_최고최저를_고른다() {
        List<IndicatorPoint<IchimokuValue>> points = SMALL.over(irregular());

        assertThat(points).hasSize(4);
        assertThat(points.getFirst().value()).isEqualTo(new IchimokuValue(
                Price.of("137.50"), Price.of("125"),
                Price.of("110"), Price.of("140"),
                Optional.of(Price.of("165"))));
    }

    /** 선행스팬 1(110)이 2(140) 아래인 하락 구름. 구름 구간은 110 ~ 140 이다. */
    @Test
    void 선행스팬_1이_2_아래면_하락_구름이다() {
        IchimokuValue value = SMALL.over(irregular()).getFirst().value();

        assertThat(value.bullishCloud()).isFalse();
        assertThat(value.positionOf(Price.of("145"))).isEqualTo(BandPosition.ABOVE);
        assertThat(value.positionOf(Price.of("120"))).isEqualTo(BandPosition.INSIDE);
        assertThat(value.positionOf(Price.of("100"))).isEqualTo(BandPosition.BELOW);
    }

    /**
     * index 12. 선행 1 = (index 9 의 전환 137.5 + 기준 125)/2 = 131.25 —
     * 스케일 2 에서 정확히 표현되므로 반올림이 개입하지 않는다.
     */
    @Test
    void 비단조_13봉의_마지막_값도_손계산과_일치한다() {
        IchimokuValue value = SMALL.over(irregular()).getLast().value();

        assertThat(value).isEqualTo(new IchimokuValue(
                Price.of("147.50"), Price.of("142.50"),
                Price.of("131.25"), Price.of("120"),
                Optional.empty()));
    }

    // ---------------------------------------------------------------------
    // 워밍업과 설정 검증
    // ---------------------------------------------------------------------

    @Test
    void 표준_설정은_캔들_78개가_있어야_값이_하나_나온다() {
        assertThat(STANDARD.over(IndicatorFixtures.rising(78, 10, 5))).hasSize(1);

        assertThatThrownBy(() -> STANDARD.over(IndicatorFixtures.rising(77, 10, 5)))
                .isInstanceOf(InsufficientCandlesException.class)
                .hasMessageContaining("78")
                .hasMessageContaining("77");
    }

    @Test
    void 빈_묶음도_부족으로_거부한다() {
        assertThatThrownBy(() -> STANDARD.over(CandleSeries.empty()))
                .isInstanceOf(InsufficientCandlesException.class);
    }

    @Test
    void 기간_순서가_뒤집히면_거부한다() {
        assertThatThrownBy(() -> new IchimokuCloud(26, 9, 52, 26))
                .isInstanceOf(InvalidIndicatorException.class)
                .hasMessageContaining("순서");
        assertThatThrownBy(() -> new IchimokuCloud(9, 52, 26, 26))
                .isInstanceOf(InvalidIndicatorException.class);
    }

    @Test
    void 기간과_변위는_1_이상이어야_한다() {
        assertThatThrownBy(() -> new IchimokuCloud(0, 26, 52, 26))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new IchimokuCloud(9, 0, 52, 26))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new IchimokuCloud(9, 26, 0, 26))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new IchimokuCloud(9, 26, 52, 0))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void 캔들_묶음은_null_일_수_없다() {
        assertThatThrownBy(() -> STANDARD.over(null))
                .isInstanceOf(InvalidValueException.class);
    }

    /** 고가·저가의 극값이 구간 첫머리와 중간에 흩어져 있는 13봉. */
    private static CandleSeries irregular() {
        return CandleSeries.of(
                IndicatorFixtures.candle(0, "200", "90", "100"),
                IndicatorFixtures.candle(1, "120", "95", "115"),
                IndicatorFixtures.candle(2, "105", "85", "90"),
                IndicatorFixtures.candle(3, "130", "100", "125"),
                IndicatorFixtures.candle(4, "115", "80", "95"),
                IndicatorFixtures.candle(5, "140", "105", "135"),
                IndicatorFixtures.candle(6, "125", "90", "110"),
                IndicatorFixtures.candle(7, "150", "120", "145"),
                IndicatorFixtures.candle(8, "135", "115", "130"),
                IndicatorFixtures.candle(9, "160", "130", "155"),
                IndicatorFixtures.candle(10, "145", "125", "140"),
                IndicatorFixtures.candle(11, "155", "135", "150"),
                IndicatorFixtures.candle(12, "170", "140", "165"));
    }
}
