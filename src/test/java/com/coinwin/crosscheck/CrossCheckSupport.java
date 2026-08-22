package com.coinwin.crosscheck;

import com.coinwin.backtest.domain.AccountSettings;
import com.coinwin.backtest.domain.BacktestEngine;
import com.coinwin.backtest.domain.BacktestSpec;
import com.coinwin.backtest.domain.CapitalMode;
import com.coinwin.backtest.domain.CostModel;
import com.coinwin.backtest.domain.EntryRules;
import com.coinwin.backtest.domain.StrategySettings;
import com.coinwin.backtest.domain.TradeWindow;
import com.coinwin.backtest.domain.ZoneSettings;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import com.coinwin.market.adapter.out.binance.BinanceCandleAdapter;
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
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.client.RestClient;

/**
 * 대조표를 찍는 여러 테스트가 공유하는 것 — 계좌·비용·조합 목록과 표 형식.
 *
 * <p>표 형식을 한곳에 둔 이유는 열이 갈리면 표끼리 비교할 수 없기 때문이다. 8개월 표와 7년
 * 표를 나란히 놓는 것이 이 대조의 목적이므로, 두 표가 같은 코드로 찍히는 것이 중요하다.
 *
 * <p>모든 줄을 {@link TradeWindow} 에서 읽는다. 전체 실행이든 잘라 낸 구간이든 같은 타입이라
 * "구간 성적은 다른 식으로 낸다" 는 여지가 없다.
 */
final class CrossCheckSupport {

    static final Symbol SYMBOL = Symbol.BTC_USDT;

    static final BacktestEngine ENGINE =
            new BacktestEngine(new FixedMaintenanceMarginPolicy(Percentage.of("0.4")));

    /** 초기 자본 800 USDT / 거래당 2% / 10배. 근거는 scope.md 의 매매 방식 전제. */
    static final AccountSettings ACCOUNT = new AccountSettings(
            Money.of("800"), Percentage.of("2"), 10, CapitalMode.FIXED);

    /** 지정가 0.02% / 시장가 0.05% / 펀딩비 0.02%. */
    static final CostModel CHARGED = new CostModel(
            Percentage.of("0.02"), Percentage.of("0.05"), Percentage.of("0.02"));

    /** BTCUSDT 무기한은 2019-09-08 에 상장했다. 그 앞을 요청해도 빈 응답이 올 뿐이다. */
    static final Instant HISTORY_FROM = Instant.parse("2019-01-01T00:00:00Z");

    /**
     * 고정 종료일. {@code Instant.now()} 를 쓰면 표가 매일 달라져 어제 본 숫자와 오늘 본 숫자를
     * 비교할 수 없다 — 문서에 적어 둔 표가 다시는 재현되지 않는다는 뜻이다. 과거 캔들은 변하지
     * 않으므로 고정 구간의 표는 언제 돌려도 같다. 늘릴 때는 문서의 표도 함께 다시 찍는다.
     */
    static final Instant HISTORY_TO = Instant.parse("2026-08-01T00:00:00Z");

    private CrossCheckSupport() {
    }

    /** 상장 이후 전체 이력. 어댑터가 1,500개씩 나눠 받아 이어 붙인다. */
    static CandleSeries history(
            RestClient client, BinanceProperties properties, CandleInterval interval) {
        return new BinanceCandleAdapter(client, properties)
                .load(new CandleQuery(SYMBOL, interval, new TimeRange(HISTORY_FROM, HISTORY_TO)));
    }

    static TimeRange calendarYear(int year) {
        return new TimeRange(
                Instant.parse("%d-01-01T00:00:00Z".formatted(year)),
                Instant.parse("%d-01-01T00:00:00Z".formatted(year + 1)));
    }

    /** 결과가 어느 조합에서 나왔는지는 스펙에만 남아 있다. 표의 이름표를 거기서 되만든다. */
    static String labelOf(BacktestSpec spec) {
        return "%d / %s / %s".formatted(
                spec.strategy().zones().pivotLookback(),
                spec.strategy().zones().clusterMultiple().toPlainString(),
                spec.strategy().rules().stopBufferMultiple().toPlainString());
    }

    /**
     * 감도표의 18조합. 피벗 3종 × 군집 3종 × 버퍼 2종.
     *
     * <p>Phase 6 이 8개월 구간에서 돌린 것과 <b>같은 격자</b>다. 격자를 바꾸면 새 표가 옛 표의
     * 반례인지 그냥 다른 실험인지 알 수 없게 된다.
     */
    static List<Combination> grid() {
        List<Combination> combinations = new ArrayList<>();
        for (int lookback : List.of(3, 5, 8)) {
            for (String cluster : List.of("0.3", "0.5", "1.0")) {
                for (String buffer : List.of("0.5", "1.0")) {
                    combinations.add(new Combination(lookback, cluster, buffer));
                }
            }
        }
        return List.copyOf(combinations);
    }

    /**
     * 조합 하나. 지표 필터는 여기 없다 — 격자는 필터를 끈 상태로만 돌리고, 필터 효과는
     * 별도의 온오프 비교에서 본다.
     */
    record Combination(int lookback, String cluster, String buffer) {

        String label() {
            return "%d / %s / %s".formatted(lookback, cluster, buffer);
        }

        BacktestSpec spec() {
            return new BacktestSpec(placeholderQuery(),
                    new StrategySettings(
                            new ZoneSettings(lookback, new BigDecimal(cluster), 2, 14),
                            new EntryRules(new BigDecimal(buffer), new BigDecimal("1.5"), false)),
                    ACCOUNT, CHARGED);
        }
    }

    /**
     * 스펙의 조회 조건은 쓰이지 않는다. 캔들을 이미 읽어 엔진에 직접 넘기기 때문이다 —
     * 포트를 소비하는 것은 {@code backtest/application} 의 일이고 여기는 거래소를 직접 때린다.
     */
    static CandleQuery placeholderQuery() {
        return new CandleQuery(SYMBOL, CandleInterval.FOUR_HOURS,
                new TimeRange(Instant.EPOCH, Instant.EPOCH.plus(Duration.ofDays(1))));
    }

    static void header(String firstColumn) {
        System.out.printf("%-30s %7s %9s %9s %11s %11s%n",
                firstColumn, "거래", "승률%", "손익비", "순손익", "최대낙폭%");
    }

    /** 표 한 줄. 말할 수 없는 값은 {@code -} 로 찍는다 — 0 으로 찍으면 사실이 아니다. */
    static void row(String label, TradeWindow window) {
        System.out.printf("%-30s %7d %9s %9s %11s %11s%n",
                label,
                window.size(),
                window.isEmpty() ? "-" : window.tally().winRate().value().toPlainString(),
                profitFactorOf(window),
                window.tally().realizedPnl().value().toPlainString(),
                window.maxDrawdown().value().toPlainString());
    }

    private static String profitFactorOf(TradeWindow window) {
        return window.profitFactor()
                .map(BigDecimal::toPlainString)
                .map(value -> value.substring(0, Math.min(6, value.length())))
                .orElse("-");
    }
}
