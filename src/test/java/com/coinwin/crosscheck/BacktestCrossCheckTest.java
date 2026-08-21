package com.coinwin.crosscheck;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.backtest.domain.AccountSettings;
import com.coinwin.backtest.domain.BacktestEngine;
import com.coinwin.backtest.domain.BacktestResult;
import com.coinwin.backtest.domain.BacktestSpec;
import com.coinwin.backtest.domain.CapitalMode;
import com.coinwin.backtest.domain.CostModel;
import com.coinwin.backtest.domain.EntryRules;
import com.coinwin.backtest.domain.StrategySettings;
import com.coinwin.backtest.domain.ZoneSettings;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import com.coinwin.market.adapter.out.binance.BinanceCandleAdapter;
import com.coinwin.market.adapter.out.binance.BinanceClientConfig;
import com.coinwin.market.adapter.out.binance.BinanceProperties;
import com.coinwin.market.domain.CandleInterval;
import com.coinwin.market.domain.CandleQuery;
import com.coinwin.market.domain.CandleSeries;
import com.coinwin.market.domain.Symbol;
import com.coinwin.market.domain.TimeRange;
import com.coinwin.position.domain.FixedMaintenanceMarginPolicy;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestClient;

/**
 * 실제 BTCUSDT 캔들 위에서 전략을 돌려 <b>파라미터 감도를 눈으로 볼 표</b>를 찍는다.
 *
 * <p>합성 톱니에서는 되돌림이 언제나 일어나 진 거래가 하나도 없었다. 실제 데이터에서는
 * 전략이 어떻게 깨지는지, 거래가 몇 건이나 나오는지, 수수료가 무엇을 먹는지가 드러난다.
 * 명세 § 12.3 이 "구현 후 실제 캔들로 돌려 보고 정한다" 고 남겨 둔 항목이다.
 *
 * <p><b>가장 좋은 조합을 고르는 것이 목적이 아니다.</b> 이 표에서 최고 성적을 낸 파라미터를
 * 기본값으로 박으면 그것은 과최적화이고, 이 도구의 목적은 좋은 성적표가 아니라 재현 가능한
 * 측정이다. 보는 것은 <b>거래 수가 통계로 쓸 만한 범위인가</b>와 <b>수치가 파라미터에 얼마나
 * 민감한가</b> 둘이다.
 *
 * <p>이 패키지가 {@code backtest} 밖인 이유는 완료 조건 때문이다 — "{@code backtest} 에
 * 바이낸스 관련 코드가 한 줄도 없다". 여기는 거래소를 직접 때린다.
 *
 * <p>기본 {@code test} 에서 제외한다. 값이 매번 달라 회귀 테스트가 될 수 없다.
 * 실행은 {@code .\gradlew.bat crossCheck} 다.
 */
@Tag("crosscheck")
@SpringBootTest(
        classes = BinanceClientConfig.class,
        properties = {
            "coinwin.market.binance.base-url=${COINWIN_BINANCE_URL:https://fapi.binance.com}",
            "coinwin.market.binance.connect-timeout=5s",
            "coinwin.market.binance.read-timeout=15s"
        })
class BacktestCrossCheckTest {

    private static final Symbol SYMBOL = Symbol.BTC_USDT;
    private static final CandleInterval INTERVAL = CandleInterval.FOUR_HOURS;

    /** 4시간봉 1,500개 ≈ 250일. 일목 워밍업 77봉을 빼도 표본이 충분하다. */
    private static final int BARS = 1500;

    private static final BacktestEngine ENGINE =
            new BacktestEngine(new FixedMaintenanceMarginPolicy(Percentage.of("0.4")));

    private static final AccountSettings ACCOUNT = new AccountSettings(
            Money.of("800"), Percentage.of("2"), 10, CapitalMode.FIXED);

    private static final CostModel CHARGED = new CostModel(
            Percentage.of("0.02"), Percentage.of("0.05"), Percentage.of("0.02"));

    private static final DateTimeFormatter UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    @Autowired
    private RestClient binanceRestClient;

    @Autowired
    private BinanceProperties properties;

    @Test
    void 실제_BTCUSDT_캔들로_파라미터_감도표를_출력한다() {
        CandleSeries series = loadRecentCandles();
        printHeader(series);

        printGrid(series);
        printComparisons(series);
        printGuide();

        assertThat(series.size()).isGreaterThan(77);
    }

    /** 닫힌 봉만 받는다. 진행 중인 봉을 섞으면 같은 명령이 같은 표를 내지 않는다. */
    private CandleSeries loadRecentCandles() {
        Instant to = Instant.now().truncatedTo(ChronoUnit.HOURS);
        Instant from = to.minus(INTERVAL.length().multipliedBy(BARS));
        return new BinanceCandleAdapter(binanceRestClient, properties)
                .load(new CandleQuery(SYMBOL, INTERVAL, new TimeRange(from, to)));
    }

    /** 파라미터 셋마다 한 줄. 어느 축이 결과를 흔드는지 보려는 것이다. */
    private static void printGrid(CandleSeries series) {
        System.out.printf("%n── 파라미터 감도 (필터 끔, 비용 켬, 고정 잔고) ──%n");
        header("피벗", "군집", "버퍼");
        for (int lookback : List.of(3, 5, 8)) {
            for (String cluster : List.of("0.3", "0.5", "1.0")) {
                for (String buffer : List.of("0.5", "1.0")) {
                    BacktestSpec spec = spec(lookback, cluster, buffer, false);
                    row("%d / %s / %s".formatted(lookback, cluster, buffer),
                            ENGINE.run(spec, series));
                }
            }
        }
    }

    /** 기본 설정 하나를 잡고 필터·비용·복리를 각각 뒤집어 본다. */
    private static void printComparisons(CandleSeries series) {
        BacktestSpec base = spec(5, "0.5", "1.0", false);

        System.out.printf("%n── 같은 설정에서 한 가지씩 뒤집기 (피벗 5 / 군집 0.5 / 버퍼 1.0) ──%n");
        header("항목", "", "");
        row("기준 (필터 끔, 비용 켬)", ENGINE.run(base, series));
        row("지표 필터 켬", ENGINE.run(base.withIndicatorFilter(true), series));
        row("비용 없음", ENGINE.run(base.withCosts(CostModel.free()), series));
        row("복리", ENGINE.run(base.withCapitalMode(CapitalMode.COMPOUND), series));
    }

    private static BacktestSpec spec(
            int lookback, String cluster, String buffer, boolean filter) {
        return new BacktestSpec(
                new CandleQuery(SYMBOL, INTERVAL, new TimeRange(
                        Instant.EPOCH, Instant.EPOCH.plus(Duration.ofDays(1)))),
                new StrategySettings(
                        new ZoneSettings(lookback, new BigDecimal(cluster), 2, 14),
                        new EntryRules(new BigDecimal(buffer), new BigDecimal("1.5"), filter)),
                ACCOUNT, CHARGED);
    }

    private static void header(String a, String b, String c) {
        System.out.printf("%-28s %7s %9s %9s %11s %11s%n",
                (a + " / " + b + " / " + c).replace(" /  / ", ""),
                "거래", "승률%", "손익비", "순손익", "최대낙폭%");
    }

    private static void row(String label, BacktestResult result) {
        System.out.printf("%-28s %7d %9s %9s %11s %11s%n",
                label,
                result.totalTrades(),
                result.totalTrades() == 0 ? "-" : result.winRate().value().toPlainString(),
                result.profitFactor().map(BigDecimal::toPlainString)
                        .map(value -> value.substring(0, Math.min(6, value.length()))).orElse("-"),
                result.netPnl().value().toPlainString(),
                result.maxDrawdown().value().toPlainString());
    }

    private static void printHeader(CandleSeries series) {
        System.out.printf("%n=== %s %s — 실제 캔들 백테스트 ===%n", SYMBOL.value(), INTERVAL.code());
        System.out.printf("캔들 %d개, %s ~ %s, 초기 자본 800 USDT / 거래당 2%% / 10배%n",
                series.size(), UTC.format(series.first().openTime()),
                UTC.format(series.last().openTime()));
    }

    private static void printGuide() {
        System.out.printf("%n── 이 표를 어떻게 읽는가 ──%n");
        System.out.println("최고 성적 조합을 기본값으로 박지 않는다. 그것은 과최적화이고,");
        System.out.println("이 구간에서만 맞는 숫자를 전략의 성질로 착각하게 만든다.");
        System.out.println("보는 것은 둘이다 — 거래 수가 통계로 쓸 만한가, 수치가 파라미터에");
        System.out.println("얼마나 민감한가. 한 칸 바꿨는데 결과가 뒤집히면 그 파라미터는");
        System.out.println("전략이 아니라 잡음을 고르고 있는 것이다.");
    }
}
