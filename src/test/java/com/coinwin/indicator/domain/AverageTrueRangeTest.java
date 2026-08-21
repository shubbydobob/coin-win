package com.coinwin.indicator.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Money;
import com.coinwin.indicator.IndicatorFixtures;
import com.coinwin.market.domain.CandleSeries;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * ATR golden test.
 *
 * <p>기댓값은 구현이 아니라 <b>TR 이 상수가 되는 캔들열</b>에서 나온다. {@code rising(n, 100, 50)}
 * 의 TR 은 {@code max(고저폭 100, 갭상승 150, |100−50| 50) = 150} 으로 두 번째 봉부터 일정하고,
 * 첫 봉만 직전 종가가 없어 {@code 고가−저가 = 100} 이다. 그래서 14봉 시드는
 * {@code (100 + 13×150) / 14 = 2050/14} 라는 닫힌 식이 되고, 이후는 Wilder 점화식으로 손으로
 * 이어진다. 값이 구현과 독립적으로 유도된다.
 *
 * <p>공식은 트레이딩뷰가 배포하는 Pine 소스 원문으로 확정했다 — {@code docs/adr/018}.
 */
class AverageTrueRangeTest {

    private static final AverageTrueRange STANDARD = AverageTrueRange.standard();

    @Test
    void 트레이딩뷰_기본값은_14봉이다() {
        assertThat(STANDARD.period()).isEqualTo(14);
    }

    /**
     * TR 이 일정한 20봉. 첫 봉 100, 나머지 150.
     *
     * <ul>
     *   <li>index 13 (시드) = (100 + 13×150) / 14 = 2050/14 = 146.428571… → 146.43
     *   <li>index 14 = 150/14 + (13/14)(2050/14) = 28750/196 = 146.683673… → 146.68
     *   <li>index 15 = 150/14 + (13/14)(28750/196) = 403150/2744 = 146.924198… → 146.92
     * </ul>
     */
    @Test
    void 등차_20봉의_ATR_은_손계산_값과_센트까지_일치한다() {
        List<IndicatorPoint<Money>> points = STANDARD.over(IndicatorFixtures.rising(20, 100, 50));

        assertThat(points).hasSize(7);
        assertThat(points.getFirst().at()).isEqualTo(IndicatorFixtures.hour(13));
        assertThat(points.get(0).value()).isEqualTo(Money.of("146.43"));
        assertThat(points.get(1).value()).isEqualTo(Money.of("146.68"));
        assertThat(points.get(2).value()).isEqualTo(Money.of("146.92"));
    }

    /**
     * 첫 값이 나오는 자리는 {@code period − 1} 이다. {@code period} 가 아니다.
     *
     * <p>{@code ta.tr(true)} 가 첫 봉에도 값을 내기 때문이다 — 직전 종가가 없으면 고가−저가로
     * 대신한다. 첫 봉을 버리는 구현이면 모든 값이 한 칸 밀리고, 그래도 "그럴듯한" 숫자가 나온다.
     */
    @Test
    void 첫_값은_period_번째가_아니라_period_직전_인덱스에_나온다() {
        List<IndicatorPoint<Money>> points =
                new AverageTrueRange(3).over(IndicatorFixtures.rising(5, 100, 50));

        assertThat(points).hasSize(3);
        assertThat(points.getFirst().at()).isEqualTo(IndicatorFixtures.hour(2));
        assertThat(points.getLast().at()).isEqualTo(IndicatorFixtures.hour(4));
    }

    /** 고가·저가·종가가 모두 같은 캔들열은 TR 이 상수이고, 상수열의 Wilder 평활은 그 상수다. */
    @Test
    void 변동폭이_일정하면_ATR_은_그_변동폭에서_움직이지_않는다() {
        List<IndicatorPoint<Money>> points = STANDARD.over(IndicatorFixtures.rising(30, 0, 50));

        assertThat(points).isNotEmpty();
        assertThat(points).allSatisfy(point -> assertThat(point.value()).isEqualTo(Money.of("100")));
    }

    /**
     * 갭. 직전 종가 95 에서 고가 120 으로 열리면 TR 은 고저폭 2 가 아니라 25 다.
     *
     * <p>고저폭만 보는 구현은 갭을 통째로 놓친다. 손절 버퍼가 ATR 에 매달려 있으므로 그 구현은
     * 갭이 잦은 구간에서 버퍼를 실제 위험보다 좁게 잡는다.
     *
     * <p>period 2 로 손계산: TR = [10, 25, 2] → 시드 (10+25)/2 = 17.5 → 2/2 + 17.5/2 = 9.75
     */
    @Test
    void 갭이_고저폭보다_크면_갭이_TR_이_된다() {
        CandleSeries series = new CandleSeries(List.of(
                IndicatorFixtures.candle(0, "100", "90", "95"),
                IndicatorFixtures.candle(1, "120", "118", "119"),
                IndicatorFixtures.candle(2, "121", "119", "120")));

        List<IndicatorPoint<Money>> points = new AverageTrueRange(2).over(series);

        assertThat(points).extracting(IndicatorPoint::value)
                .containsExactly(Money.of("17.50"), Money.of("9.75"));
    }

    /** 하락 갭도 대칭으로 잡는다. 직전 종가 120 에서 저가 100 으로 열리면 TR 은 20 이다. */
    @Test
    void 하락_갭도_TR_에_반영된다() {
        CandleSeries series = new CandleSeries(List.of(
                IndicatorFixtures.candle(0, "121", "119", "120"),
                IndicatorFixtures.candle(1, "102", "100", "101")));

        List<IndicatorPoint<Money>> points = new AverageTrueRange(2).over(series);

        // TR = [2, max(2, |102−120|=18, |100−120|=20) = 20] → 시드 (2+20)/2 = 11
        assertThat(points).extracting(IndicatorPoint::value).containsExactly(Money.of("11"));
    }

    /**
     * 점화식은 <b>반올림하지 않은</b> 값으로 이어 간다.
     *
     * <p>매 단계 스케일 2 로 스냅하면 오차가 누적된다. 30봉을 지나면 센트가 갈리고, 그 오차는
     * ATR 배수로 정의된 대 폭과 손절 버퍼로 그대로 번진다. 같은 원칙이 볼린저 상하단
     * ({@code docs/adr/015})과 자산 곡선({@code docs/adr/010})에도 적용돼 있다.
     */
    @Test
    void 긴_시리즈에서도_점화식_누적_오차가_생기지_않는다() {
        // TR 이 상수 150 인 구간이 길어지면 ATR 은 150 으로 수렴한다. 단계마다 스냅하면
        // 반올림 잔재가 남아 149.99 나 150.01 에서 멈춘다.
        List<IndicatorPoint<Money>> points = STANDARD.over(IndicatorFixtures.rising(400, 100, 50));

        assertThat(points.getLast().value()).isEqualTo(Money.of("150"));
    }

    @Test
    void 캔들이_기간보다_적으면_필요한_개수를_알려_준다() {
        assertThatThrownBy(() -> STANDARD.over(IndicatorFixtures.rising(13, 100, 50)))
                .isInstanceOf(InsufficientCandlesException.class)
                .hasMessageContaining("14")
                .hasMessageContaining("13");
    }

    @Test
    void 기간은_1_이상이어야_한다() {
        assertThatThrownBy(() -> new AverageTrueRange(0)).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void null_캔들_묶음은_거부된다() {
        assertThatThrownBy(() -> STANDARD.over(null)).isInstanceOf(InvalidValueException.class);
    }
}
