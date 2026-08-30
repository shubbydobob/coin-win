package com.coinwin.crosscheck;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.market.adapter.out.binance.BinanceCandleAdapter;
import com.coinwin.market.adapter.out.binance.BinanceClientConfig;
import com.coinwin.market.adapter.out.binance.BinanceProperties;
import com.coinwin.market.domain.Candle;
import com.coinwin.market.domain.CandleInterval;
import com.coinwin.market.domain.CandleQuery;
import com.coinwin.market.domain.CandleSeries;
import com.coinwin.market.domain.Symbol;
import com.coinwin.market.domain.TimeRange;
import com.coinwin.readout.domain.IndicatorStance;
import com.coinwin.readout.domain.Stance;
import com.coinwin.readout.domain.TimeframeSeries;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestClient;

/**
 * <b>롱·숏 딱지가 실제로 유리한 쪽을 가리키는가.</b>
 *
 * <p>화면은 다섯 지표가 지금 어느 쪽에 서 있는지를 센다. 그것은 정의로 정해지는 사실이고,
 * 이 저장소는 <b>거기서 멈춘다</b> — "그러니 롱이 유리하다" 를 적지 않는 이유는 그 문장에
 * 증거가 없기 때문이다. 이 대조가 그 증거를 실제로 찾아본다.
 *
 * <p><b>재는 방법.</b> 지표를 <b>한 번</b> 계산하고(계산기가 인과적이라 각 점은 그 봉까지의
 * 정보만 쓴다) 봉마다 그때의 딱지를 읽는다. 그 다음 N봉의 종가 수익률을 딱지별로 모아
 * 승률과 중앙값을 낸다. <b>기준선(전체 평균)을 반드시 함께 찍는다</b> — 롱 승률 52% 는 전체가
 * 52% 면 아무 의미가 없다.
 *
 * <p><b>판정 규칙을 이쪽에 복사하지 않았다.</b> {@code TimeframeSeries.stancesAt} 을 그대로
 * 부른다. 복사하면 화면과 측정이 다른 규칙을 쓰게 되고, 그러면 이 표를 화면에 되돌려 읽을 수
 * 없다.
 *
 * <p><b>이 표가 답하지 못하는 것.</b> 다중검정 보정도 국면 분리도 여기 없다. 다섯 지표 ×
 * 세 기간 = 열다섯 번을 보는 것이므로 <b>p&lt;0.05 기준이면 아무 예측력이 없어도 한 번쯤은
 * 유의하게 나온다.</b> 그 보정은 연구 파이프라인(`research/`)의 일이고, 여기서 볼 것은
 * 크기다 — 기준선과의 차이가 수수료·슬리피지(왕복 0.14% 안팎)를 넘는가.
 *
 * <p>{@code check} 는 네트워크 없이 돌아야 하므로 {@code crosscheck} 로 뗀다.
 */
@Tag("crosscheck")
@SpringBootTest(
        classes = BinanceClientConfig.class,
        properties = {
            "coinwin.market.binance.base-url=${COINWIN_BINANCE_URL:https://fapi.binance.com}",
            "coinwin.market.binance.connect-timeout=5s",
            "coinwin.market.binance.read-timeout=30s"
        })
class StanceCrossCheckTest {

    private static final Symbol SYMBOL = Symbol.BTC_USDT;

    private static final CandleInterval INTERVAL = CandleInterval.FOUR_HOURS;

    /**
     * 고정 구간. <b>{@code Instant.now()} 를 쓰면 표가 매일 달라지고</b>, 문서에 적어 둔 수가
     * 다시는 재현되지 않는다 — {@code docs/adr/021} 이 같은 이유로 구간을 박았다.
     */
    private static final Instant FROM = Instant.parse("2019-09-08T00:00:00Z");

    private static final Instant TO = Instant.parse("2026-08-01T00:00:00Z");

    /** 몇 봉 뒤를 보는가. 4시간봉이므로 각각 4시간 · 하루 · 이레다. */
    private static final List<Integer> HORIZONS = List.of(1, 6, 42);

    private static final MathContext MC = new MathContext(12);

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Autowired
    private RestClient binanceRestClient;

    @Autowired
    private BinanceProperties properties;

    @Test
    void 딱지가_유리한_쪽을_가리키는지_잰다() {
        CandleSeries series = candles();
        assertThat(series.size()).isGreaterThan(10_000);
        TimeframeSeries indicators = TimeframeSeries.over(INTERVAL, series);
        List<Candle> bars = series.candles();

        System.out.printf("%n%s %s · %d봉 · %s ~ %s%n",
                SYMBOL.value(), INTERVAL.code(), bars.size(),
                bars.get(0).openTime(), bars.get(bars.size() - 1).openTime());

        for (int horizon : HORIZONS) {
            표를_찍는다(bars, indicators, horizon);
        }
        for (int horizon : HORIZONS) {
            조합표를_찍는다(bars, indicators, horizon);
        }
    }

    private void 표를_찍는다(List<Candle> bars, TimeframeSeries indicators, int horizon) {
        Map<String, Map<Stance, Bucket>> byIndicator = new LinkedHashMap<>();
        Bucket baseline = new Bucket();

        for (int i = 0; i < bars.size() - horizon; i++) {
            BigDecimal ret = 수익률(bars, i, horizon);
            baseline.add(ret);
            for (IndicatorStance stance : indicators.stancesAt(bars.get(i).openTime())) {
                byIndicator
                        .computeIfAbsent(stance.indicator(), key -> new LinkedHashMap<>())
                        .computeIfAbsent(stance.stance(), key -> new Bucket())
                        .add(ret);
            }
        }

        System.out.printf("%n=== %d봉 뒤(%d시간) 종가 수익률 ===%n", horizon, horizon * 4);
        System.out.printf("%-10s %-6s %8s %9s %10s %10s%n",
                "지표", "딱지", "표본", "승률", "중앙값", "기준선차");
        System.out.printf("%-10s %-6s %8d %8.2f%% %9.4f%% %10s%n",
                "(전체)", "-", baseline.count, baseline.winRate(), baseline.median(), "-");
        byIndicator.forEach((indicator, buckets) -> buckets.forEach((stance, bucket) -> {
            if (stance == Stance.UNKNOWN || bucket.count < 1_000) {
                return;
            }
            System.out.printf("%-10s %-6s %8d %8.2f%% %9.4f%% %+9.2f%%p%n",
                    indicator, stance, bucket.count, bucket.winRate(), bucket.median(),
                    bucket.winRate() - baseline.winRate());
        }));
    }

    /**
     * 일목과 볼린저를 <b>함께</b> 본 표. 화면이 주기마다 "구름 위 · 밴드 안" 처럼 두 딱지를
     * 나란히 적으므로, 사람이 실제로 읽는 것은 낱개가 아니라 이 조합이다.
     *
     * <p><b>낱개 표로는 답할 수 없는 질문이다.</b> 일목이 롱이고 볼린저가 중립인 칸의 승률은
     * 두 낱개 승률에서 나오지 않는다 — 두 딱지가 같은 봉에서 함께 서는 빈도가 편향돼 있으면
     * 곱셈이 성립하지 않는다.
     *
     * <p>조합이 아홉이고 기간이 셋이라 <b>검정이 스물일곱</b>이 된다. 여기서 p 를 내지 않는
     * 이유가 그것이다 — 보정 없이 스물일곱을 보면 아무 예측력이 없어도 몇 개는 좋아 보인다.
     * 볼 것은 크기이고, 기준선과의 차이가 왕복 비용(0.14% 안팎)을 넘는가만 묻는다.
     */
    private void 조합표를_찍는다(List<Candle> bars, TimeframeSeries indicators, int horizon) {
        Map<String, Bucket> byPair = new LinkedHashMap<>();
        Bucket baseline = new Bucket();

        for (int i = 0; i < bars.size() - horizon; i++) {
            BigDecimal ret = 수익률(bars, i, horizon);
            baseline.add(ret);
            byPair.computeIfAbsent(조합이름(indicators, bars.get(i).openTime()),
                    key -> new Bucket()).add(ret);
        }

        System.out.printf("%n=== 일목 × 볼린저 · %d봉 뒤(%d시간) ===%n", horizon, horizon * 4);
        System.out.printf("%-26s %8s %9s %10s %10s%n",
                "조합", "표본", "승률", "중앙값", "기준선차");
        System.out.printf("%-26s %8d %8.2f%% %9.4f%% %10s%n",
                "(전체)", baseline.count, baseline.winRate(), baseline.median(), "-");
        byPair.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().count, a.getValue().count))
                .forEach(e -> {
                    if (e.getValue().count < 1_000) {
                        return;
                    }
                    System.out.printf("%-26s %8d %8.2f%% %9.4f%% %+9.2f%%p%n",
                            e.getKey(), e.getValue().count, e.getValue().winRate(),
                            e.getValue().median(),
                            e.getValue().winRate() - baseline.winRate());
                });
    }

    /**
     * 그 봉의 (일목, 볼린저) 딱지를 한 이름으로. <b>규칙을 복사하지 않는다</b> —
     * {@code stancesAt} 이 낸 것을 이름만 붙여 묶는다.
     */
    private static String 조합이름(TimeframeSeries indicators, Instant bar) {
        Map<String, Stance> found = new LinkedHashMap<>();
        for (IndicatorStance stance : indicators.stancesAt(bar)) {
            found.put(stance.indicator(), stance.stance());
        }
        return "일목 " + found.getOrDefault("일목", Stance.UNKNOWN)
                + " · 볼린저 " + found.getOrDefault("볼린저", Stance.UNKNOWN);
    }

    /** 종가 대비 종가. <b>MFE·MAE 는 여기서 재지 않는다</b> — 그것은 1분봉이 있어야 한다. */
    private static BigDecimal 수익률(List<Candle> bars, int index, int horizon) {
        BigDecimal now = bars.get(index).close().value();
        BigDecimal later = bars.get(index + horizon).close().value();
        return later.subtract(now).divide(now, MC).multiply(HUNDRED, MC);
    }

    /** 한 딱지에 모인 수익률들. */
    private static final class Bucket {
        private final List<BigDecimal> returns = new ArrayList<>();
        private int count;
        private int wins;

        void add(BigDecimal ret) {
            returns.add(ret);
            count++;
            if (ret.signum() > 0) {
                wins++;
            }
        }

        double winRate() {
            return count == 0 ? 0 : wins * 100.0 / count;
        }

        /** 중앙값. <b>평균이 아니다</b> — 한 번의 큰 움직임이 표를 통째로 끌고 간다. */
        double median() {
            if (returns.isEmpty()) {
                return 0;
            }
            List<BigDecimal> sorted = new ArrayList<>(returns);
            sorted.sort(BigDecimal::compareTo);
            return sorted.get(sorted.size() / 2).setScale(6, RoundingMode.HALF_UP).doubleValue();
        }
    }

    private CandleSeries candles() {
        BinanceCandleAdapter adapter = new BinanceCandleAdapter(binanceRestClient, properties);
        return adapter.load(new CandleQuery(SYMBOL, INTERVAL, new TimeRange(FROM, TO)));
    }
}
