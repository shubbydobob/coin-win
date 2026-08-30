package com.coinwin.readout.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.indicator.domain.BollingerValue;
import com.coinwin.indicator.domain.IchimokuCloud;
import com.coinwin.indicator.domain.IndicatorPoint;
import com.coinwin.indicator.domain.MacdValue;
import com.coinwin.market.domain.Candle;
import com.coinwin.market.domain.CandleSeries;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntToDoubleFunction;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>한 봉만 봐서는 나오지 않는 값들.</b>
 *
 * <p>밴드폭이 좁은지, 밖에서 며칠째 걷고 있는지, 시그널 위에 선 지 얼마나 됐는지, 후행스팬이
 * 그때의 가격보다 위인지 — 전부 마지막 값 하나로는 답할 수 없고, 그래서 딱지에서 통째로
 * 빠져 있던 것들이다. 근거는 {@code docs/spec/indicator-usage.md} § 4.
 */
class IndicatorHistoryTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    private static final Money ATR = Money.of(new BigDecimal("500"));

    /**
     * <b>수축은 절대값이 아니라 순위다.</b> 3% 가 좁은지 넓은지는 그 종목의 최근 이력 위에서만
     * 뜻을 갖는다 — 100 이면 이 창에서 가장 넓다.
     */
    @Test
    @DisplayName("밴드 폭은 창 안에서 몇 번째인지로 말한다")
    void 밴드폭_순위() {
        BollingerReadout 판독 = BollingerReadout.over(밴드점(), 봉());

        // 다섯 중 지금(3.3333%)보다 좁거나 같은 것이 넷이다.
        assertThat(판독.bandWidthRank().value()).isEqualByComparingTo("80.0000");
    }

    /**
     * <b>「밖」 하나로는 방금 나간 것과 사흘째 걷는 것이 같은 사실이 된다.</b> 부호를 함께
     * 실어 내므로 방향과 길이를 부르는 쪽이 다시 맞댈 필요가 없다.
     */
    @Test
    @DisplayName("밴드 밖에 연속으로 머문 봉을 센다")
    void 밴드_걷기() {
        assertThat(BollingerReadout.over(밴드점(), 봉()).bandWalk()).isEqualTo(3);
    }

    @Test
    @DisplayName("지금 안에 있으면 걷기는 0 이다")
    void 안에_있으면() {
        CandleSeries 안쪽 = 봉(60000, 60000, 62000, 62000, 60000);

        assertThat(BollingerReadout.over(밴드점(), 안쪽).bandWalk()).isZero();
    }

    /** 부호가 바뀐 뒤로 몇 봉인가. 방금 넘어온 것과 스무 봉째 위인 것은 다른 자리다. */
    @Test
    @DisplayName("MACD 는 지금 부호가 이어진 봉 수를 센다")
    void 교차_이후() {
        MacdReadout 판독 = MacdReadout.over(마크드(-1, 2, 3, 4), ATR);

        assertThat(판독.barsSinceCross()).isEqualTo(3);
        assertThat(판독.aboveZero()).isTrue();
        assertThat(판독.change().value()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("시그널 아래면 이어진 봉 수가 음수다")
    void 아래로_넘어가면() {
        assertThat(MacdReadout.over(마크드(2, -1, -3), ATR).barsSinceCross()).isEqualTo(-2);
    }

    /**
     * <b>200 구간이 없으면 벌어진 정도를 말할 수 없다.</b> 0 으로 채우면 "두 선이 붙어 있다"
     * 는 없는 사실이 생긴다.
     */
    @Test
    @DisplayName("봉이 200 개에 못 미치면 이동평균 간격이 비어 있다")
    void 이동평균이_없으면() {
        assertThat(MovingAverageReadout.over(단조증가(50), ATR).spread()).isEmpty();
    }

    @Test
    @DisplayName("오르는 장에서는 20 이 200 위에 있다")
    void 이동평균_간격() {
        assertThat(MovingAverageReadout.over(단조증가(300), ATR).spread().orElseThrow().value())
                .isPositive();
    }

    /**
     * 후행스팬은 지금 종가를 변위만큼 뒤로 민 선이므로, "그때의 가격보다 위인가" 는
     * <b>지금 종가와 그 봉 종가의 비교</b>와 같은 물음이다.
     */
    @Test
    @DisplayName("오르는 장에서는 후행스팬이 그때의 가격보다 위에 있다")
    void 후행스팬() {
        assertThat(IchimokuCloud.standard().laggingSpanGap(단조증가(100)).orElseThrow().value())
                .isPositive();
    }

    @Test
    @DisplayName("변위만큼 전의 봉이 없으면 후행스팬을 말할 수 없다")
    void 후행스팬이_없으면() {
        assertThat(IchimokuCloud.standard().laggingSpanGap(단조증가(10))).isEmpty();
    }

    /**
     * <b>「50 위」 는 51 과 78 을 같은 사실로 만든다.</b> 값과 그 움직임을 함께 내면 둘이
     * 갈린다 — 3봉이라는 수는 임의로 고른 것이고 검증한 적이 없다.
     */
    @Test
    @DisplayName("RSI 는 값과 3봉 전 대비 변화를 함께 낸다")
    void RSI_값과_움직임() {
        RsiReadout 판독 = RsiReadout.over(비율(40, 45, 50, 55, 62));

        assertThat(판독.value().value()).isEqualByComparingTo("62.0000");
        assertThat(판독.change3()).isEqualByComparingTo("17.0000");
    }

    /** 3봉 전이 없으면 있는 것 중 가장 앞과 견준다. 변화가 0 인 것과 같은 자리다. */
    @Test
    @DisplayName("봉이 셋에 못 미치면 있는 만큼만 견준다")
    void RSI_봉이_모자라면() {
        assertThat(RsiReadout.over(비율(40)).change3()).isEqualByComparingTo("0.0000");
    }

    private static List<IndicatorPoint<Percentage>> 비율(int... values) {
        List<IndicatorPoint<Percentage>> points = new ArrayList<>(values.length);
        for (int i = 0; i < values.length; i++) {
            points.add(new IndicatorPoint<>(START.plus(Duration.ofHours(i)),
                    Percentage.of(BigDecimal.valueOf(values[i]))));
        }
        return List.copyOf(points);
    }

    /** 폭이 다른 다섯 밴드. 마지막이 가운데 넓이라 순위가 100 도 0 도 아니다. */
    private static List<IndicatorPoint<BollingerValue>> 밴드점() {
        int[][] 값 = {{62000, 60000, 58000}, {61000, 60000, 59000}, {60500, 60000, 59500},
                {60500, 60000, 59500}, {61000, 60000, 59000}};
        List<IndicatorPoint<BollingerValue>> points = new ArrayList<>(값.length);
        for (int i = 0; i < 값.length; i++) {
            points.add(new IndicatorPoint<>(START.plus(Duration.ofHours(i)),
                    new BollingerValue(가격(값[i][0]), 가격(값[i][1]), 가격(값[i][2]))));
        }
        return List.copyOf(points);
    }

    private static List<IndicatorPoint<MacdValue>> 마크드(int... histograms) {
        List<IndicatorPoint<MacdValue>> points = new ArrayList<>(histograms.length);
        for (int i = 0; i < histograms.length; i++) {
            BigDecimal histogram = BigDecimal.valueOf(histograms[i]);
            points.add(new IndicatorPoint<>(START.plus(Duration.ofHours(i)),
                    new MacdValue(histogram, BigDecimal.ZERO, histogram)));
        }
        return List.copyOf(points);
    }

    /** 마지막 셋이 상단 밖이다 — 상단은 그 봉들에서 60,500 과 61,000 이다. */
    private static CandleSeries 봉() {
        return 봉(60000, 60000, 62000, 62000, 62000);
    }

    private static CandleSeries 봉(int... closes) {
        return new CandleSeries(IntStream.range(0, closes.length)
                .mapToObj(i -> 캔들(i, closes[i]))
                .toList());
    }

    private static CandleSeries 단조증가(int count) {
        IntToDoubleFunction close = i -> 60000 + i * 60.0;
        return new CandleSeries(IntStream.range(0, count)
                .mapToObj(i -> 캔들(i, (int) close.applyAsDouble(i)))
                .toList());
    }

    private static Candle 캔들(int index, int close) {
        BigDecimal value = BigDecimal.valueOf(close);
        return new Candle(
                START.plus(Duration.ofHours(index)),
                Price.of(value),
                Price.of(value.add(BigDecimal.ONE)),
                Price.of(value.subtract(BigDecimal.ONE)),
                Price.of(value),
                Quantity.of(BigDecimal.ONE));
    }

    private static Price 가격(int value) {
        return Price.of(BigDecimal.valueOf(value));
    }
}
