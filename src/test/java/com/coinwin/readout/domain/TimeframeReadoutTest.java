package com.coinwin.readout.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.backtest.domain.ZoneSettings;
import com.coinwin.indicator.domain.VolumeProfileSettings;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.indicator.domain.BandPosition;
import com.coinwin.indicator.domain.InsufficientCandlesException;
import com.coinwin.market.domain.Candle;
import com.coinwin.market.domain.CandleInterval;
import com.coinwin.market.domain.CandleSeries;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TimeframeReadoutTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    /**
     * <b>워밍업을 채우지 못하면 값을 지어내지 않는다.</b> 일목은 52봉, 볼린저는 20봉이 필요하다.
     * 그 전에 0 이나 직전 값으로 채우면 화면이 "구름 아래" 라고 단언하는데 실제로는
     * <b>구름이 아직 없는</b> 상태가 된다.
     *
     * <p><b>예외를 판독기가 만들지 않는다.</b> 처음에는 여기에 자체 예외를 두었는데 그 코드는
     * 절대 도달하지 못했다 — 워밍업을 아는 것은 지표이고 지표가 이미 던진다. 같은 규칙을 두
     * 곳에 두면 지표가 기간을 바꾸는 날 이쪽만 옛 규칙으로 남는다.
     */
    @Test
    void 봉이_모자라면_판독하지_않는다() {
        CandleSeries 짧은것 = 톱니(30);

        assertThatThrownBy(() -> TimeframeReadout.over(
                CandleInterval.ONE_HOUR, 짧은것, ZoneSettings.standard(), VolumeProfileSettings.standard()))
                .isInstanceOf(InsufficientCandlesException.class);
    }

    /**
     * <b>단조 상승에서는 가격이 구름 위에 있다.</b> 구름은 과거 구간의 중간값을 26봉 민 것이라
     * 오르는 장에서는 언제나 아래에 깔린다. 이것이 참이 아니면 변위 방향이 뒤집힌 것이다 —
     * Phase 4 에서 실제로 한 칸 밀려 있던 그 자리다.
     */
    @Test
    void 오르는_장에서는_구름_위에_선다() {
        TimeframeReadout 판독 = TimeframeReadout.over(
                CandleInterval.ONE_HOUR, 단조증가(200), ZoneSettings.standard(), VolumeProfileSettings.standard());

        assertThat(판독.indicators().ichimoku()).isEqualTo(BandPosition.ABOVE);
        assertThat(판독.close().value()).isGreaterThan(판독.indicators().cloudTop().value());
    }

    /** 구름 상단은 두 선행스팬 중 큰 쪽이다. 뒤집히는 것 자체가 뜻을 가지므로 순서를 고정한다. */
    @Test
    void 구름_상단은_언제나_하단보다_높거나_같다() {
        TimeframeReadout 판독 = TimeframeReadout.over(
                CandleInterval.FOUR_HOURS, 톱니(200), ZoneSettings.standard(), VolumeProfileSettings.standard());

        assertThat(판독.indicators().cloudTop().value())
                .isGreaterThanOrEqualTo(판독.indicators().cloudBottom().value());
        assertThat(판독.indicators().bollingerUpper().value())
                .isGreaterThan(판독.indicators().bollingerLower().value());
    }

    /**
     * <b>대는 있을 수도 없을 수도 있다.</b> 최소 터치 수를 채운 대가 지금 가격 아래에 하나도
     * 없으면 비어 있는 것이 정답이다 — 0 으로 채우면 화면이 "지지가 0 원" 이라고 말한다.
     */
    @Test
    void 대는_없을_수_있고_있으면_지금_가격의_반대편에_있다() {
        TimeframeReadout 판독 = TimeframeReadout.over(
                CandleInterval.FIFTEEN_MINUTES, 톱니(200), ZoneSettings.standard(), VolumeProfileSettings.standard());

        판독.support().ifPresent(대 ->
                assertThat(대.near().value()).isLessThanOrEqualTo(판독.close().value()));
        판독.resistance().ifPresent(대 ->
                assertThat(대.near().value()).isGreaterThanOrEqualTo(판독.close().value()));
    }

    /** 판독은 <b>마지막 봉</b>을 기준으로 삼는다. 실시간 화면은 봉이 닫히기를 기다리지 않는다. */
    @Test
    void 마지막_봉을_기준으로_삼는다() {
        CandleSeries 시리즈 = 단조증가(200);
        Candle 마지막 = 시리즈.candles().getLast();

        TimeframeReadout 판독 = TimeframeReadout.over(
                CandleInterval.ONE_HOUR, 시리즈, ZoneSettings.standard(), VolumeProfileSettings.standard());

        assertThat(판독.at()).isEqualTo(마지막.openTime());
        assertThat(판독.close()).isEqualTo(마지막.close());
    }

    /** 값이 한쪽으로 몰리지 않게 오르내리는 톱니. 대가 실제로 만들어지는 모양이다. */
    private static CandleSeries 톱니(int 개수) {
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < 개수; i++) {
            long 기준 = 60_000 + (i % 20 < 10 ? i % 20 : 20 - i % 20) * 300L;
            candles.add(봉(i, 기준));
        }
        return new CandleSeries(candles);
    }

    private static CandleSeries 단조증가(int 개수) {
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < 개수; i++) {
            candles.add(봉(i, 60_000 + i * 50L));
        }
        return new CandleSeries(candles);
    }

    private static Candle 봉(int index, long 기준) {
        BigDecimal open = BigDecimal.valueOf(기준);
        return new Candle(
                START.plus(Duration.ofHours(index)),
                Price.of(open),
                Price.of(open.add(BigDecimal.valueOf(120))),
                Price.of(open.subtract(BigDecimal.valueOf(120))),
                Price.of(open.add(BigDecimal.valueOf(40))),
                new Quantity(new BigDecimal("1.5")));
    }
}
