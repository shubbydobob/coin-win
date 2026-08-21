package com.coinwin.backtest.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import com.coinwin.indicator.domain.AverageTrueRange;
import com.coinwin.indicator.domain.BandPosition;
import com.coinwin.indicator.domain.BollingerBands;
import com.coinwin.indicator.domain.IchimokuCloud;
import com.coinwin.indicator.domain.IndicatorPoint;
import com.coinwin.market.domain.Candle;
import com.coinwin.market.domain.CandleSeries;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * 시점별로 <b>그때 알 수 있는 것만</b> 꺼내 주는 타임라인. 룩어헤드 방지가 이 클래스에 모여 있다.
 *
 * <p>위험한 곳이 셋이고 전부 여기서 막는다.
 *
 * <ol>
 *   <li><b>피벗은 {@code t − lookback} 까지만 확정된다.</b> index {@code i} 가 극값인지 알려면
 *       {@code i + lookback} 봉이 필요하다. {@link Pivot#isKnownAt} 로 거른다.
 *   <li><b>후행스팬을 쓰지 않는다.</b> 시점 {@code t} 의 {@code laggingSpan} 에는 {@code t+25}
 *       봉의 종가가 담긴다. 여기서 꺼내는 것은 구름 위치({@code positionOf})뿐이고, 그것은
 *       25봉 전 데이터를 앞으로 민 선행스팬으로만 계산된다.
 *   <li><b>인덱스 산술로 맞추지 않는다.</b> 지표마다 워밍업이 달라 앞이 잘리는 길이가 다르다.
 *       시각을 키로 맞춘다.
 * </ol>
 *
 * <p>지표를 시리즈 전체에 대해 미리 계산하는 것은 룩어헤드가 아니다. {@code t} 의 값이
 * {@code t} 이전 캔들로만 결정되기 때문이다. 그 성질이 실제로 성립하는지는 접미사 불변
 * 테스트가 확인한다.
 */
final class MarketTimeline {

    private final List<Candle> candles;
    private final ZoneSettings settings;
    private final List<Pivot> pivots;
    private final Map<Instant, Money> atr;
    private final Map<Instant, BandPosition> ichimoku;
    private final Map<Instant, BandPosition> bollinger;

    private MarketTimeline(CandleSeries series, ZoneSettings settings) {
        this.candles = series.candles();
        this.settings = settings;
        this.pivots = new PivotDetector(settings.pivotLookback()).over(series);
        Map<Instant, Price> closes = closesByTime(this.candles);
        this.atr = index(new AverageTrueRange(settings.atrPeriod()).over(series),
                (at, value) -> value);
        this.ichimoku = index(IchimokuCloud.standard().over(series),
                (at, value) -> value.positionOf(closes.get(at)));
        this.bollinger = index(BollingerBands.standard().over(series),
                (at, value) -> value.positionOf(closes.get(at)));
    }

    static MarketTimeline over(CandleSeries series, ZoneSettings settings) {
        DomainValues.required(series, "캔들 묶음");
        DomainValues.required(settings, "대 설정");
        return new MarketTimeline(series, settings);
    }

    /** 세 지표가 모두 값을 갖는 첫 인덱스. 그 앞은 판단할 근거가 없다. */
    int firstTradableIndex() {
        for (int i = 0; i < candles.size(); i++) {
            if (allReadyAt(candles.get(i).openTime())) {
                return i;
            }
        }
        return candles.size();
    }

    int size() {
        return candles.size();
    }

    Candle candleAt(int index) {
        return candles.get(index);
    }

    MarketSnapshot snapshotAt(int index) {
        Candle candle = candles.get(index);
        Money currentAtr = atr.get(candle.openTime());
        List<Pivot> known = pivots.stream()
                .filter(pivot -> pivot.isKnownAt(candle.openTime()))
                .toList();
        return new MarketSnapshot(candle.openTime(), candle.close(), currentAtr,
                ZoneMap.from(known, settings.toleranceFor(currentAtr), settings.minTouches()));
    }

    IndicatorReading readingAt(int index) {
        Instant at = candles.get(index).openTime();
        return new IndicatorReading(ichimoku.get(at), bollinger.get(at));
    }

    private boolean allReadyAt(Instant at) {
        return atr.containsKey(at) && ichimoku.containsKey(at) && bollinger.containsKey(at);
    }

    /**
     * 지표 판정은 <b>그 봉의 종가</b>로 낸다. 지표선끼리 비교하면 값은 나오지만 뜻이 없다 —
     * 물어보는 것은 "가격이 구름의 어디에 있는가" 이지 "전환선이 구름의 어디에 있는가" 가 아니다.
     */
    private static <T, R> Map<Instant, R> index(
            List<IndicatorPoint<T>> points, BiFunction<Instant, T, R> extract) {
        Map<Instant, R> byTime = new HashMap<>();
        points.forEach(point -> byTime.put(point.at(), extract.apply(point.at(), point.value())));
        return Map.copyOf(byTime);
    }

    private static Map<Instant, Price> closesByTime(List<Candle> candles) {
        Map<Instant, Price> closes = new HashMap<>();
        candles.forEach(candle -> closes.put(candle.openTime(), candle.close()));
        return Map.copyOf(closes);
    }
}
