package com.coinwin.crosscheck;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.backtest.domain.BacktestSpec;
import com.coinwin.backtest.domain.CapitalMode;
import com.coinwin.backtest.domain.CostModel;
import com.coinwin.crosscheck.CrossCheckSupport.Combination;
import com.coinwin.market.adapter.out.binance.BinanceCandleAdapter;
import com.coinwin.market.adapter.out.binance.BinanceClientConfig;
import com.coinwin.market.adapter.out.binance.BinanceProperties;
import com.coinwin.market.domain.CandleInterval;
import com.coinwin.market.domain.CandleQuery;
import com.coinwin.market.domain.CandleSeries;
import com.coinwin.market.domain.TimeRange;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestClient;

/**
 * 최근 구간의 파라미터 감도와 <b>한 가지씩 뒤집어 보기</b>.
 *
 * <p>합성 톱니에서는 되돌림이 언제나 일어나 진 거래가 하나도 없었다. 실제 데이터에서는 전략이
 * 어떻게 깨지는지, 거래가 몇 건이나 나오는지, 수수료가 무엇을 먹는지가 드러난다. 명세 § 12.3
 * 이 "구현 후 실제 캔들로 돌려 보고 정한다" 고 남겨 둔 항목이다.
 *
 * <p><b>가장 좋은 조합을 고르는 것이 목적이 아니다.</b> 이 표에서 최고 성적을 낸 파라미터를
 * 기본값으로 박으면 그것은 과최적화이고, 이 도구의 목적은 좋은 성적표가 아니라 재현 가능한
 * 측정이다.
 *
 * <p><b>이 표만으로 전략을 판단하지 않는다.</b> 1,500봉은 8개월이고, 같은 격자를 7년에서
 * 돌리면 답이 달라진다 — {@link LongHorizonCrossCheckTest} 와 {@code docs/adr/021} 을 본다.
 * 여기 남겨 두는 이유는 <b>짧은 구간이 무엇을 보여주는가</b> 자체가 그 문서의 근거이기
 * 때문이다. 구간이 흐르므로 이 표의 숫자는 실행할 때마다 달라진다.
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

    private static final CandleInterval INTERVAL = CandleInterval.FOUR_HOURS;

    /** 4시간봉 1,500개 ≈ 250일. 일목 워밍업 77봉을 빼도 한 화면 분량의 표가 나온다. */
    private static final int BARS = 1500;

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
                .load(new CandleQuery(CrossCheckSupport.SYMBOL, INTERVAL,
                        new TimeRange(from, to)));
    }

    /** 파라미터 셋마다 한 줄. 어느 축이 결과를 흔드는지 보려는 것이다. */
    private static void printGrid(CandleSeries series) {
        System.out.printf("%n── 파라미터 감도 (필터 끔, 비용 켬, 고정 잔고) ──%n");
        CrossCheckSupport.header("피벗 / 군집 / 버퍼");
        for (Combination combination : CrossCheckSupport.grid()) {
            CrossCheckSupport.row(combination.label(),
                    CrossCheckSupport.ENGINE.run(combination.spec(), series).window());
        }
    }

    /** 기본 설정 하나를 잡고 필터·비용·복리를 각각 뒤집어 본다. */
    private static void printComparisons(CandleSeries series) {
        BacktestSpec base = new Combination(5, "0.5", "1.0").spec();

        System.out.printf("%n── 같은 설정에서 한 가지씩 뒤집기 (피벗 5 / 군집 0.5 / 버퍼 1.0) ──%n");
        CrossCheckSupport.header("항목");
        row("기준 (필터 끔, 비용 켬)", base, series);
        row("지표 필터 켬", base.withIndicatorFilter(true), series);
        row("비용 없음", base.withCosts(CostModel.free()), series);
        row("복리", base.withCapitalMode(CapitalMode.COMPOUND), series);
    }

    private static void row(String label, BacktestSpec spec, CandleSeries series) {
        CrossCheckSupport.row(label, CrossCheckSupport.ENGINE.run(spec, series).window());
    }

    private static void printHeader(CandleSeries series) {
        System.out.printf("%n=== %s %s — 최근 %d봉 백테스트 ===%n",
                CrossCheckSupport.SYMBOL.value(), INTERVAL.code(), BARS);
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
        System.out.println();
        System.out.println("그리고 이 구간은 8개월이다. 7년 표는 LongHorizonCrossCheckTest 가,");
        System.out.println("구간 밖 성적은 WalkForwardCrossCheckTest 가 찍는다 — docs/adr/021.");
    }
}
