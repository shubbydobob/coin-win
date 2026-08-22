package com.coinwin.crosscheck;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.backtest.domain.BacktestResult;
import com.coinwin.backtest.domain.TradeWindow;
import com.coinwin.crosscheck.CrossCheckSupport.Combination;
import com.coinwin.market.adapter.out.binance.BinanceClientConfig;
import com.coinwin.market.adapter.out.binance.BinanceProperties;
import com.coinwin.market.domain.CandleInterval;
import com.coinwin.market.domain.CandleSeries;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestClient;

/**
 * 같은 격자를 <b>상장 이후 전체 이력</b>에서 돌린다. Phase 6 의 표가 구간의 성질이었는지
 * 전략의 성질이었는지를 가르는 것이 목적이다.
 *
 * <p>Phase 6 의 감도표는 4시간봉 1,500개 — 8개월이다. 그 표에서 손익비 1.33 을 낸 조합이
 * 하나 있었고, 로드맵은 그것을 기본값으로 박지 않기로 하면서 <b>"표본 수를 먼저 확보하지
 * 않으면 같은 함정에 빠진다"</b> 고 적었다. 이 테스트가 그 표본이다.
 *
 * <p>구간을 <b>고정</b>한다. {@code Instant.now()} 를 쓰면 표가 매일 달라져 어제 본 숫자와
 * 오늘 본 숫자를 비교할 수 없다 — 문서에 적어 둔 표가 다시는 재현되지 않는다는 뜻이다.
 * 과거 캔들은 변하지 않으므로 고정 구간의 표는 언제 돌려도 같다.
 *
 * <p>기본 {@code test} 에서 제외한다. 거래소를 때리므로 회귀 테스트가 될 수 없다.
 * 실행은 {@code .\gradlew.bat crossCheck} 다.
 */
@Tag("crosscheck")
@SpringBootTest(
        classes = BinanceClientConfig.class,
        properties = {
            "coinwin.market.binance.base-url=${COINWIN_BINANCE_URL:https://fapi.binance.com}",
            "coinwin.market.binance.connect-timeout=5s",
            "coinwin.market.binance.read-timeout=30s"
        })
class LongHorizonCrossCheckTest {

    private static final DateTimeFormatter UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    @Autowired
    private RestClient binanceRestClient;

    @Autowired
    private BinanceProperties properties;

    @Test
    void 전체_이력에서_파라미터_감도와_연도별_분해를_출력한다() {
        CandleSeries series = fullHistory(CandleInterval.FOUR_HOURS);
        printHeader(series, CandleInterval.FOUR_HOURS);

        List<BacktestResult> results = CrossCheckSupport.grid().stream()
                .map(combination -> CrossCheckSupport.ENGINE.run(combination.spec(), series))
                .toList();

        printGrid(results);
        printByYear(results);
        printGuide();

        assertThat(series.size()).isGreaterThan(10_000);
    }

    /** 같은 전략을 다른 주기에서. 설정이 전부 봉 수와 ATR 배수라 재조정 없이 옮겨진다. */
    @Test
    void 같은_설정을_주기별로_돌린다() {
        System.out.printf("%n── 주기 대조 (피벗 5 / 군집 0.5 / 버퍼 1.0, 전체 이력) ──%n");
        CrossCheckSupport.header("주기");

        for (CandleInterval interval : List.of(
                CandleInterval.ONE_HOUR, CandleInterval.FOUR_HOURS, CandleInterval.ONE_DAY)) {
            CandleSeries series = fullHistory(interval);
            BacktestResult result = CrossCheckSupport.ENGINE.run(baseline().spec(), series);
            CrossCheckSupport.row(
                    "%s (%d봉)".formatted(interval.code(), series.size()), result.window());
        }
    }

    private static Combination baseline() {
        return new Combination(5, "0.5", "1.0");
    }

    private CandleSeries fullHistory(CandleInterval interval) {
        return CrossCheckSupport.history(binanceRestClient, properties, interval);
    }

    private static void printGrid(List<BacktestResult> results) {
        System.out.printf("%n── 파라미터 감도, 전체 이력 (필터 끔, 비용 켬, 고정 잔고) ──%n");
        CrossCheckSupport.header("피벗 / 군집 / 버퍼");
        List<Combination> grid = CrossCheckSupport.grid();
        for (int i = 0; i < grid.size(); i++) {
            CrossCheckSupport.row(grid.get(i).label(), results.get(i).window());
        }
    }

    /**
     * 기본 조합 하나를 연도로 갈라 본다. 전체 손익비가 1 을 넘더라도 그것이 한 해에서만
     * 나온 것이면 전략의 성질이 아니다.
     */
    private static void printByYear(List<BacktestResult> results) {
        BacktestResult base = results.get(CrossCheckSupport.grid().indexOf(baseline()));

        System.out.printf("%n── 연도별 분해 (피벗 5 / 군집 0.5 / 버퍼 1.0) ──%n");
        CrossCheckSupport.header("연도");
        for (int year = 2019; year <= 2026; year++) {
            CrossCheckSupport.row(String.valueOf(year), TradeWindow.enteredWithin(
                    CrossCheckSupport.calendarYear(year), base.trades(),
                    CrossCheckSupport.ACCOUNT.initialCapital()));
        }
    }

    private void printHeader(CandleSeries series, CandleInterval interval) {
        System.out.printf("%n=== %s %s — 전체 이력 백테스트 ===%n",
                CrossCheckSupport.SYMBOL.value(), interval.code());
        System.out.printf("캔들 %d개, %s ~ %s, 초기 자본 800 USDT / 거래당 2%% / 10배%n",
                series.size(), UTC.format(series.first().openTime()),
                UTC.format(series.last().openTime()));
    }

    private static void printGuide() {
        System.out.printf("%n── 8개월 표와 무엇을 비교하는가 ──%n");
        System.out.println("같은 격자다. 다른 것은 구간 길이뿐이다.");
        System.out.println("보는 것은 셋이다 — 8개월에서 1 을 넘던 조합이 7년에서도 넘는가,");
        System.out.println("거래 수가 늘어 순위가 안정됐는가, 연도별로 부호가 뒤집히는가.");
        System.out.println("연도별로 뒤집힌다면 전체 수치는 평균일 뿐 전략의 성질이 아니다.");
    }
}
