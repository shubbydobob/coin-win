package com.coinwin.indicator;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.indicator.domain.BollingerBands;
import com.coinwin.indicator.domain.BollingerValue;
import com.coinwin.indicator.domain.IchimokuCloud;
import com.coinwin.indicator.domain.IchimokuValue;
import com.coinwin.indicator.domain.IndicatorPoint;
import com.coinwin.market.adapter.out.binance.BinanceCandleAdapter;
import com.coinwin.market.adapter.out.binance.BinanceClientConfig;
import com.coinwin.market.adapter.out.binance.BinanceProperties;
import com.coinwin.market.domain.Candle;
import com.coinwin.market.domain.CandleInterval;
import com.coinwin.market.domain.CandleQuery;
import com.coinwin.market.domain.CandleSeries;
import com.coinwin.market.domain.Symbol;
import com.coinwin.market.domain.TimeRange;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestClient;

/**
 * 트레이딩뷰 대조용 출력. <b>단언이 아니라 눈으로 볼 표를 찍는 것이 목적이다.</b>
 *
 * <p>Phase 4 완료 조건은 "트레이딩뷰 값과 일치" 인데 그 값은 코드로 가져올 수 없다(ADR 015).
 * golden test 가 증명하는 것은 "구현이 명시된 공식을 정확히 따른다" 까지이고, 공식 해석 자체가
 * 트레이딩뷰와 같은지는 사람이 차트를 열어 확인해야 한다.
 *
 * <p><b>변위 26 과 25 를 나란히 찍는 것이 이 테스트의 핵심이다.</b> 두 규약이 모두 통용되고
 * 공개 문서로 갈리지 않으므로(ADR 014), 같은 캔들에서 두 값을 함께 보여 주면 차트를 한 번만
 * 봐도 어느 쪽이 맞는지 정해진다. 전환선·기준선은 변위와 무관하므로 <b>그것부터 맞아야</b>
 * 한다 — 거기서 어긋나면 변위가 아니라 공식이 틀린 것이다.
 *
 * <p>기본 {@code test} 에서 제외한다. 진짜 거래소를 때리므로 네트워크가 없으면 실패하고,
 * 값이 매번 달라 회귀 테스트가 될 수 없다. 실행은 {@code .\gradlew.bat crossCheck} 다.
 */
@Tag("crosscheck")
@SpringBootTest(
        classes = BinanceClientConfig.class,
        properties = {
            // 테스트용 application.yml 은 base-url 을 localhost:1 로 막아 둔다.
            // 이 테스트만 의도적으로 진짜 거래소를 때리므로 여기서 명시적으로 되돌린다.
            "coinwin.market.binance.base-url=${COINWIN_BINANCE_URL:https://fapi.binance.com}",
            "coinwin.market.binance.connect-timeout=5s",
            "coinwin.market.binance.read-timeout=15s"
        })
class TradingViewCrossCheckTest {

    private static final Symbol SYMBOL = Symbol.BTC_USDT;
    private static final CandleInterval INTERVAL = CandleInterval.ONE_HOUR;

    /** 일목 표준 설정은 78봉이 있어야 값이 하나 나온다. 여유를 둔다. */
    private static final int HOURS = 400;

    /** 표에 찍을 최근 시점 수. 다섯 줄이면 한 화면에서 비교된다. */
    private static final int ROWS = 5;

    private static final IchimokuCloud DISPLACED_26 = IchimokuCloud.standard();
    private static final IchimokuCloud DISPLACED_25 = new IchimokuCloud(9, 26, 52, 25);
    private static final BollingerBands BOLLINGER = BollingerBands.standard();

    private static final DateTimeFormatter UTC =
            DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter KST =
            DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.of("Asia/Seoul"));

    @Autowired
    private RestClient binanceRestClient;

    @Autowired
    private BinanceProperties properties;

    @Test
    void 실제_BTCUSDT_캔들로_트레이딩뷰_대조표를_출력한다() {
        CandleSeries series = loadRecentCandles();

        List<IndicatorPoint<IchimokuValue>> ichimoku26 = DISPLACED_26.over(series);
        List<IndicatorPoint<IchimokuValue>> ichimoku25 = DISPLACED_25.over(series);
        List<IndicatorPoint<BollingerValue>> bollinger = BOLLINGER.over(series);

        printHeader(series);
        printIchimokuCommon(series, ichimoku26);
        printLeadingSpans(series, ichimoku26, ichimoku25);
        printBollinger(bollinger);
        printGuide();

        assertThat(series.size()).isGreaterThanOrEqualTo(78);
        assertThat(ichimoku26.getLast().at()).isEqualTo(series.last().openTime());
        assertThat(bollinger).hasSize(series.size() - BOLLINGER.period() + 1);
    }

    /**
     * 닫힌 봉만 받는다. 끝 시각을 정시로 내리면 진행 중인 봉이 구간 밖으로 나간다.
     *
     * <p>진행 중인 봉을 섞으면 대조 도중에 값이 바뀌어 무엇과 무엇을 비교했는지 알 수 없다.
     */
    private CandleSeries loadRecentCandles() {
        Instant to = Instant.now().truncatedTo(ChronoUnit.HOURS);
        Instant from = to.minus(Duration.ofHours(HOURS));
        BinanceCandleAdapter adapter = new BinanceCandleAdapter(binanceRestClient, properties);
        return adapter.load(new CandleQuery(SYMBOL, INTERVAL, new TimeRange(from, to)));
    }

    private static void printHeader(CandleSeries series) {
        Instant last = series.last().openTime();
        System.out.printf("%n=== %s %s — 트레이딩뷰 대조 ===%n",
                SYMBOL.value(), INTERVAL.code());
        System.out.printf("받은 캔들 %d개, 마지막 닫힌 봉 %s UTC (%s KST)%n",
                series.size(), UTC.format(last), KST.format(last));
        System.out.printf("마지막 종가 %s%n", series.last().close().value());
    }

    /**
     * 전환선과 기준선은 변위의 영향을 받지 않는다. <b>여기가 먼저 맞아야 한다.</b>
     * 이 둘이 어긋나면 변위 논쟁 이전에 이동 최대·최소나 기간이 틀린 것이다.
     */
    private static void printIchimokuCommon(
            CandleSeries series, List<IndicatorPoint<IchimokuValue>> points) {
        System.out.printf("%n── 일목: 전환선·기준선 (변위와 무관) ──%n");
        System.out.printf("%-12s %12s %12s %12s%n", "시각(UTC)", "종가", "전환선", "기준선");
        Map<Instant, Candle> byTime = byOpenTime(series);
        for (IndicatorPoint<IchimokuValue> point : lastRows(points)) {
            System.out.printf("%-12s %12s %12s %12s%n",
                    UTC.format(point.at()),
                    byTime.get(point.at()).close().value(),
                    point.value().conversionLine().value(),
                    point.value().baseLine().value());
        }
    }

    /**
     * 같은 시점의 선행스팬을 두 변위로 나란히 찍는다. 차트의 구름과 맞는 쪽이 옳은 규약이다.
     *
     * <p>구름 위치는 <b>그 시점의 종가</b>로 판정한다. 트레이딩뷰에서 캔들이 구름 위에 있는지
     * 눈으로 본 것과 대조하면 된다.
     */
    private static void printLeadingSpans(
            CandleSeries series,
            List<IndicatorPoint<IchimokuValue>> displaced26,
            List<IndicatorPoint<IchimokuValue>> displaced25) {
        System.out.printf("%n── 일목: 선행스팬 — 변위 26 vs 25 ──%n");
        System.out.printf("%-12s %11s %11s %8s │ %11s %11s %8s%n",
                "시각(UTC)", "선행1(26)", "선행2(26)", "위치", "선행1(25)", "선행2(25)", "위치");
        Map<Instant, Candle> byTime = byOpenTime(series);
        Map<Instant, IchimokuValue> by25 = displaced25.stream()
                .collect(Collectors.toMap(
                        IndicatorPoint::at, IndicatorPoint::value));
        for (IndicatorPoint<IchimokuValue> point : lastRows(displaced26)) {
            IchimokuValue with26 = point.value();
            IchimokuValue with25 = by25.get(point.at());
            var close = byTime.get(point.at()).close();
            System.out.printf("%-12s %11s %11s %8s │ %11s %11s %8s%n",
                    UTC.format(point.at()),
                    with26.leadingSpanA().value(), with26.leadingSpanB().value(),
                    with26.positionOf(close),
                    with25.leadingSpanA().value(), with25.leadingSpanB().value(),
                    with25.positionOf(close));
        }
    }

    private static void printBollinger(List<IndicatorPoint<BollingerValue>> points) {
        System.out.printf("%n── 볼린저 밴드 20 / 2 (모집단 표준편차) ──%n");
        System.out.printf("%-12s %12s %12s %12s %9s%n",
                "시각(UTC)", "상단", "중심선", "하단", "밴드폭%");
        for (IndicatorPoint<BollingerValue> point : lastRows(points)) {
            BollingerValue value = point.value();
            System.out.printf("%-12s %12s %12s %12s %9s%n",
                    UTC.format(point.at()),
                    value.upper().value(), value.middle().value(), value.lower().value(),
                    value.bandWidth().value());
        }
    }

    private static void printGuide() {
        System.out.printf("""

                ── 확인 방법 ──
                트레이딩뷰에서 BINANCE:BTCUSDT.P 1시간 차트를 열고
                Ichimoku Cloud(9/26/52/26) 와 Bollinger Bands(20/2) 를 올린다.
                차트 시간대를 UTC 로 맞춘 뒤 위 시각의 값과 비교한다.

                1) 전환선·기준선이 먼저 맞아야 한다. 어긋나면 변위가 아니라 공식 문제다.
                2) 선행스팬은 26 열과 25 열 중 차트와 맞는 쪽을 고른다.
                   25 가 맞으면 IchimokuCloud.standard() 의 변위를 25 로 바꾼다.
                3) 결과는 docs/adr/014 에 기록한다.
                %n""");
    }

    private static <T> List<IndicatorPoint<T>> lastRows(List<IndicatorPoint<T>> points) {
        return points.subList(Math.max(0, points.size() - ROWS), points.size());
    }

    private static Map<Instant, Candle> byOpenTime(CandleSeries series) {
        return series.candles().stream()
                .collect(Collectors.toMap(
                        Candle::openTime, Function.identity()));
    }
}
