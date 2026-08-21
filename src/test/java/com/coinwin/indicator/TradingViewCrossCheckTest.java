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
 * golden test 가 증명하는 것은 "구현이 명시된 공식을 정확히 따른다" 까지이므로, 실제 시장
 * 데이터에서도 값이 맞는지는 사람이 차트를 열어 확인한다.
 *
 * <p>변위 규약(26 입력 / 25 이동)은 트레이딩뷰가 배포하는 Pine 소스 원문으로 확정했다.
 * 근거는 ADR 014 — 더 이상 두 규약을 나란히 찍지 않는다.
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

    /** 일목 표준 설정은 77봉이 있어야 값이 하나 나온다. 여유를 둔다. */
    private static final int HOURS = 400;

    /** 표에 찍을 최근 시점 수. 다섯 줄이면 한 화면에서 비교된다. */
    private static final int ROWS = 5;

    private static final IchimokuCloud ICHIMOKU = IchimokuCloud.standard();
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

        List<IndicatorPoint<IchimokuValue>> ichimoku = ICHIMOKU.over(series);
        List<IndicatorPoint<BollingerValue>> bollinger = BOLLINGER.over(series);

        printHeader(series);
        printIchimoku(series, ichimoku);
        printBollinger(bollinger);
        printGuide();

        assertThat(series.size()).isGreaterThanOrEqualTo(77);
        assertThat(ichimoku.getLast().at()).isEqualTo(series.last().openTime());
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
     * 선행스팬은 25봉 전에 계산된 값이다(트레이딩뷰 {@code offset = displacement − 1}).
     * 구름 위치는 그 시점의 종가로 판정하므로 차트에서 캔들이 구름 위에 있는지와 맞대면 된다.
     */
    private static void printIchimoku(
            CandleSeries series, List<IndicatorPoint<IchimokuValue>> points) {
        System.out.printf("%n── 일목균형표 9 / 26 / 52, 변위 26 (실제 이동 25봉) ──%n");
        System.out.printf("%-12s %11s %11s %11s %11s %11s %7s%n",
                "시각(UTC)", "종가", "전환선", "기준선", "선행1", "선행2", "위치");
        Map<Instant, Candle> byTime = byOpenTime(series);
        for (IndicatorPoint<IchimokuValue> point : lastRows(points)) {
            IchimokuValue value = point.value();
            var close = byTime.get(point.at()).close();
            System.out.printf("%-12s %11s %11s %11s %11s %11s %7s%n",
                    UTC.format(point.at()), close.value(),
                    value.conversionLine().value(), value.baseLine().value(),
                    value.leadingSpanA().value(), value.leadingSpanB().value(),
                    value.positionOf(close));
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
                차트 시간대를 UTC 로 맞춘 뒤 위 시각에 십자선을 두고 값을 비교한다.

                선행스팬은 차트에서 25봉 앞에 그려진다. 십자선을 캔들 위에 두면
                범례에 나오는 값이 위 표의 선행1·선행2 와 같아야 한다.
                %n""");
    }

    private static <T> List<IndicatorPoint<T>> lastRows(List<IndicatorPoint<T>> points) {
        return points.subList(Math.max(0, points.size() - ROWS), points.size());
    }

    private static Map<Instant, Candle> byOpenTime(CandleSeries series) {
        return series.candles().stream()
                .collect(Collectors.toMap(Candle::openTime, Function.identity()));
    }
}
