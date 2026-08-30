package com.coinwin.readout.domain;

import com.coinwin.backtest.domain.Pivot;
import com.coinwin.backtest.domain.PivotDetector;
import com.coinwin.backtest.domain.ZoneMap;
import com.coinwin.backtest.domain.ZoneSettings;
import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import com.coinwin.indicator.domain.AverageTrueRange;
import com.coinwin.indicator.domain.BollingerBands;
import com.coinwin.indicator.domain.BollingerValue;
import com.coinwin.indicator.domain.IchimokuCloud;
import com.coinwin.indicator.domain.IchimokuValue;
import com.coinwin.indicator.domain.IndicatorPoint;
import com.coinwin.indicator.domain.VolumeProfile;
import com.coinwin.indicator.domain.VolumeProfileSettings;
import com.coinwin.market.domain.CandleInterval;
import com.coinwin.market.domain.CandleSeries;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 한 주기가 지금 무엇을 말하는가.
 *
 * <p><b>이 타입이 있는 이유는 계산기가 화면에 한 번도 닿은 적이 없었기 때문이다.</b> 일목과
 * 볼린저는 Phase 4 에서 트레이딩뷰 Pine 소스 원문까지 대조해 확정했고, 대는 Phase 6 에서
 * 7년 15,110봉으로 검증했다. 그런데 그 둘은 <b>백테스트 안에서만</b> 돌았다 — 매일 화면을
 * 보며 판단하는 사람에게는 없는 것과 같았다.
 *
 * <p><b>피벗을 한 번만 잡아 둘이 나눠 쓴다.</b> 대와 피보나치가 각자 피벗을 잡으면 같은
 * 화면의 두 지표가 서로 다른 스윙을 가리킬 수 있다 — 계산은 같아도 그렇게 되는 순간
 * 사람이 둘을 맞대어 볼 수 없다.
 *
 * <p><b>계산기를 새로 만들지 않는다.</b> {@code indicator} 와 {@code backtest.domain} 의 것을
 * 그대로 부른다. 여기서 같은 식을 다시 쓰면 화면이 보여 주는 값과 백테스트가 검증한 값이
 * 갈라지고, 그 순간 검증은 아무것도 말해 주지 않는다.
 *
 * <p><b>방향을 말하지 않는다.</b> "구름 위이고 지지대에서 0.4% 위" 는 사실이고 "그러니 롱" 은
 * 예측이다. 이 저장소는 그 종류의 전제를 반증했다({@code docs/adr/021}). 담는 것은 위치와
 * 값까지이며, 어느 쪽으로 갈지와 얼마나 걸지는 사람과 계획 화면이 정한다.
 *
 * @param interval 어느 주기인가
 * @param at 판독의 기준이 된 봉의 시각
 * @param close 그 봉의 종가. 아래 모든 위치 판정이 이 값 기준이다
 * @param atr 이 시점의 변동성. 대의 폭과 손절 버퍼가 이 단위로 정해진다
 * @param indicators 일목과 볼린저
 * @param support 아래에서 가장 가까운 대. 없을 수 있다
 * @param resistance 위에서 가장 가까운 대. 없을 수 있다
 * @param fibonacci 마지막 스윙에 걸친 되돌림. 스윙 한쪽이 없으면 비어 있다
 * @param volume 매물대. <b>대와 다른 것을 잰다</b> — 대는 몇 번 되돌아섰나를 세고 매물대는
 *     거기서 얼마나 오갔나를 센다. 둘이 같은 자리를 가리키면 그것이 두 개의 증거다
 */
public record TimeframeReadout(
        CandleInterval interval,
        Instant at,
        Price close,
        Money atr,
        IndicatorReadout indicators,
        Optional<ZoneReadout> support,
        Optional<ZoneReadout> resistance,
        Optional<FibonacciRetracement> fibonacci,
        VolumeProfileReadout volume) {

    public TimeframeReadout {
        DomainValues.required(interval, "주기");
        DomainValues.required(at, "시각");
        DomainValues.required(close, "종가");
        DomainValues.required(atr, "ATR");
        DomainValues.required(indicators, "지표");
        DomainValues.required(support, "지지대");
        DomainValues.required(resistance, "저항대");
        DomainValues.required(fibonacci, "피보나치");
        DomainValues.required(volume, "매물대");
    }

    /**
     * 캔들에서 판독을 만든다.
     *
     * <p><b>마지막 봉을 기준으로 삼는다.</b> 그 봉은 아직 닫히지 않았을 수 있고, 그래서
     * 여기 나오는 값들은 봉이 닫히면 달라진다 — 백테스트가 닫힌 봉만 보는 것과 다른 점이며,
     * 실시간 화면에서는 그것이 맞다. 사람은 봉이 닫히기를 기다렸다 진입하지 않는다.
     */
    public static TimeframeReadout over(
            CandleInterval interval,
            CandleSeries series,
            ZoneSettings zoneSettings,
            VolumeProfileSettings volumeSettings) {
        DomainValues.required(interval, "주기");
        DomainValues.required(series, "캔들");
        DomainValues.required(zoneSettings, "대 설정");
        DomainValues.required(volumeSettings, "매물대 설정");
        Price close = lastCandleClose(series);
        Money atr = last(new AverageTrueRange(zoneSettings.atrPeriod()).over(series));
        List<Pivot> pivots = new PivotDetector(zoneSettings.pivotLookback()).over(series);
        ZoneMap zones = ZoneMap.from(
                pivots, zoneSettings.toleranceFor(atr), zoneSettings.minTouches());
        return new TimeframeReadout(
                interval, lastCandleTime(series), close, atr,
                indicatorsOf(series, close, atr),
                zones.nearestSupport(close).map(zone -> ZoneReadout.of(zone, close)),
                zones.nearestResistance(close).map(zone -> ZoneReadout.of(zone, close)),
                FibonacciRetracement.over(pivots),
                VolumeProfileReadout.of(VolumeProfile.over(series, volumeSettings), close));
    }

    private static IndicatorReadout indicatorsOf(CandleSeries series, Price close, Money atr) {
        IchimokuValue ichimoku = last(IchimokuCloud.standard().over(series));
        BollingerValue bollinger = last(BollingerBands.standard().over(series));
        return IndicatorReadout.of(ichimoku, bollinger, close, atr);
    }

    private static Price lastCandleClose(CandleSeries series) {
        return series.candles().getLast().close();
    }

    private static Instant lastCandleTime(CandleSeries series) {
        return series.candles().getLast().openTime();
    }

    /**
     * 지표의 마지막 값.
     *
     * <p><b>비어 있는 경우를 여기서 막지 않는다.</b> 처음에는 막았는데, 그 가드는 절대
     * 도달하지 못하는 코드였다 — 워밍업이 모자라면 계산기가 이미 {@code
     * InsufficientCandlesException} 을 던지고 목록은 애초에 비어서 돌아오지 않는다.
     *
     * <p>여기 같은 규칙을 또 두면 <b>"봉이 몇 개면 값을 낼 수 있는가" 가 두 곳에 생긴다.</b>
     * 지표가 기간을 바꾸는 날 이쪽은 조용히 옛 규칙으로 남고, 그 어긋남은 아무도 모른다.
     * 워밍업을 아는 것은 지표이고 이 타입은 그 답을 옮기기만 한다.
     */
    private static <T> T last(List<IndicatorPoint<T>> points) {
        return points.getLast().value();
    }
}
