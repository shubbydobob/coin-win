package com.coinwin.research;

import com.coinwin.backtest.domain.ZoneSettings;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.indicator.domain.AtrMultiple;
import com.coinwin.indicator.domain.AverageTrueRange;
import com.coinwin.indicator.domain.BandRatio;
import com.coinwin.indicator.domain.BollingerValue;
import com.coinwin.indicator.domain.IchimokuCloud;
import com.coinwin.indicator.domain.IchimokuValue;
import com.coinwin.indicator.domain.IndicatorPoint;
import com.coinwin.indicator.domain.MacdValue;
import com.coinwin.market.domain.Candle;
import com.coinwin.market.domain.CandleInterval;
import com.coinwin.market.domain.CandleSeries;
import com.coinwin.readout.domain.IndicatorDerivations;
import com.coinwin.readout.domain.IndicatorKind;
import com.coinwin.readout.domain.IndicatorStance;
import com.coinwin.readout.domain.MovingAverageLine;
import com.coinwin.readout.domain.TimeframeSeries;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 6년치 봉마다 {@code docs/spec/indicator-usage.md} § 4 의 칸을 찍는다.
 *
 * <p><b>왜 자바가 찍는가.</b> 지표 정의가 두 곳이 되면 화면이 보여 주는 값과 측정이 판정한
 * 값이 갈라진다. 파이썬은 1분봉을 접어 CSV 로 주고({@code research/bars.py}) 결과를 슬롯
 * 격자에 붙이기만 한다 — <b>정의는 이쪽에만 있다</b>(§ 5.2).
 *
 * <p><b>화면이 쓰는 그 타입을 그대로 부른다.</b> {@link TimeframeSeries} 는 판독 차트가 쓰는
 * 것이고 {@link IndicatorDerivations} 는 판독 요약이 쓰는 것이다.
 *
 * <p><b>봉 마감 시각을 함께 찍는다.</b> 슬롯에 붙이는 쪽이 "그 시각에 이미 닫힌 봉" 을 고를
 * 수 있어야 하고, 그 규칙이 없으면 룩어헤드가 된다(§ 5.3).
 *
 * <p><b>봉마다 값을 미리 표로 만들어 둔다.</b> 줄을 쓸 때 목록을 뒤지면 21만 봉에서 그 자체가
 * 돌지 않는다 — 지표 계산기들이 전 구간을 한 번 훑는 것과 같은 모양을 지킨다.
 *
 * <p>게이트 밖이다 — {@code .\gradlew.bat indicatorDump} 로만 돈다.
 */
@Tag("indicatorDump")
class IndicatorDumpTest {

    private static final Path DATA = Path.of("research", "data");

    private static final Map<String, CandleInterval> INTERVALS = new LinkedHashMap<>(Map.of(
            "4h", CandleInterval.FOUR_HOURS,
            "1h", CandleInterval.ONE_HOUR,
            "15m", CandleInterval.FIFTEEN_MINUTES));

    private static final String HEADER = String.join(",",
            "open_time", "close_time", "cloud_position", "cloud_bullish", "cloud_thickness_atr",
            "tk_gap_atr", "kijun_dist_atr", "lagging_gap_atr", "pct_b", "bandwidth",
            "bandwidth_rank", "band_walk", "rsi", "rsi_change3", "macd_hist_atr",
            "macd_change_atr", "macd_zero_side", "macd_bars_since_cross", "ma_order",
            "ma_spread_atr");

    @Test
    void 봉마다_지표를_찍는다() throws IOException {
        for (Map.Entry<String, CandleInterval> entry : INTERVALS.entrySet()) {
            Path source = DATA.resolve("bars-" + entry.getKey() + ".csv");
            if (!Files.exists(source)) {
                System.out.printf("%s 없음 — research/bars.py 를 먼저 돌린다%n", source);
                continue;
            }
            List<String[]> cells = cells(source);
            CandleSeries series = new CandleSeries(cells.stream().map(IndicatorDumpTest::candle).toList());
            List<String> rows = new Dump(entry.getValue(), series, closeTimes(cells)).rows();
            Path target = DATA.resolve("indicators-" + entry.getKey() + ".csv");
            Files.write(target, prepend(rows));
            System.out.printf("%-4s 봉 %,d → %,d행  %s%n",
                    entry.getKey(), series.candles().size(), rows.size(), target);
        }
    }

    /** 봉마다 한 줄. 값은 전부 미리 만들어 둔 표에서 찾는다. */
    private static final class Dump {

        private final CandleSeries series;

        private final Map<Instant, Long> closeTimes;

        private final Map<Instant, IchimokuValue> ichimoku;

        private final Map<Instant, BollingerValue> bollinger;

        private final Map<Instant, Percentage> rsi;

        private final Map<Instant, BigDecimal> rsiChange;

        private final Map<Instant, MacdValue> macd;

        private final Map<Instant, BigDecimal> macdChange;

        private final Map<Instant, Money> atr;

        private final Map<Instant, Percentage> ranks;

        private final Map<Instant, Integer> walks;

        private final Map<Instant, Integer> runs;

        private final Map<Instant, Money> laggingGaps;

        private final Map<Instant, String> maOrders;

        private final Map<Instant, AtrMultiple> maSpreads;

        Dump(CandleInterval interval, CandleSeries series, Map<Instant, Long> closeTimes) {
            TimeframeSeries computed = TimeframeSeries.over(interval, series);
            this.series = series;
            this.closeTimes = closeTimes;
            this.ichimoku = index(computed.ichimoku());
            this.bollinger = index(computed.bollinger());
            this.rsi = index(computed.rsi());
            this.rsiChange = changes(computed.rsi(), 3, Percentage::value);
            this.macd = index(computed.macd());
            this.macdChange = changes(computed.macd(), 1, value -> value.histogram());
            this.atr = index(new AverageTrueRange(ZoneSettings.standard().atrPeriod()).over(series));
            this.ranks = align(computed.bollinger(),
                    IndicatorDerivations.bandWidthRanks(computed.bollinger()));
            this.walks = align(computed.bollinger(),
                    IndicatorDerivations.bandWalks(computed.bollinger(), series));
            this.runs = align(computed.macd(), IndicatorDerivations.macdRuns(computed.macd()));
            this.laggingGaps = laggingGaps(series);
            this.maOrders = orders(computed, series);
            this.maSpreads = spreads(computed, atr);
        }

        List<String> rows() {
            List<String> out = new ArrayList<>(series.candles().size());
            for (Candle candle : series.candles()) {
                if (ready(candle.openTime())) {
                    out.add(row(candle));
                }
            }
            return out;
        }

        private boolean ready(Instant at) {
            return ichimoku.containsKey(at) && bollinger.containsKey(at) && rsi.containsKey(at)
                    && macd.containsKey(at) && atr.containsKey(at);
        }

        private String row(Candle candle) {
            Instant at = candle.openTime();
            Price close = candle.close();
            Money now = atr.get(at);
            return String.join(",",
                    Long.toString(at.toEpochMilli()), Long.toString(closeTimes.get(at)),
                    cloudCells(at, close, now), bandCells(at, close),
                    rsi.get(at).value().toPlainString(), rsiChange.get(at).toPlainString(),
                    macdCells(at, now), maOrders.getOrDefault(at, "UNKNOWN"),
                    plain(maSpreads.get(at)));
        }

        private String cloudCells(Instant at, Price close, Money now) {
            IchimokuValue cloud = ichimoku.get(at);
            Money gap = laggingGaps.get(at);
            return String.join(",",
                    cloud.positionOf(close).name(),
                    cloud.bullishCloud() ? "1" : "0",
                    AtrMultiple.of(cloud.cloud().width(), now).value().toPlainString(),
                    AtrMultiple.between(cloud.conversionLine(), cloud.baseLine(), now)
                            .value().toPlainString(),
                    AtrMultiple.between(close, cloud.baseLine(), now).value().toPlainString(),
                    gap == null ? "" : AtrMultiple.of(gap, now).value().toPlainString());
        }

        private String bandCells(Instant at, Price close) {
            BollingerValue band = bollinger.get(at);
            Optional<BandRatio> ratio = band.band().ratioOf(close);
            return String.join(",",
                    ratio.map(value -> value.value().toPlainString()).orElse(""),
                    band.bandWidth().value().toPlainString(),
                    ranks.get(at).value().toPlainString(),
                    Integer.toString(walks.get(at)));
        }

        private String macdCells(Instant at, Money now) {
            MacdValue value = macd.get(at);
            return String.join(",",
                    AtrMultiple.of(Money.of(value.histogram()), now).value().toPlainString(),
                    AtrMultiple.of(Money.of(macdChange.get(at)), now).value().toPlainString(),
                    value.macd().signum() > 0 ? "1" : "0",
                    Integer.toString(runs.get(at)));
        }

        private static String plain(AtrMultiple value) {
            return value == null ? "" : value.value().toPlainString();
        }
    }

    private static <T> Map<Instant, T> index(List<IndicatorPoint<T>> points) {
        Map<Instant, T> out = new HashMap<>(points.size() * 2);
        points.forEach(point -> out.put(point.at(), point.value()));
        return out;
    }

    private static <T, R> Map<Instant, R> align(List<IndicatorPoint<T>> points, List<R> values) {
        Map<Instant, R> out = new HashMap<>(values.size() * 2);
        for (int i = 0; i < values.size(); i++) {
            out.put(points.get(i).at(), values.get(i));
        }
        return out;
    }

    /** {@code back} 봉 전 대비 변화. 그만큼 전이 없으면 있는 것 중 가장 앞과 견준다. */
    private static <T> Map<Instant, BigDecimal> changes(
            List<IndicatorPoint<T>> points, int back, java.util.function.Function<T, BigDecimal> of) {
        Map<Instant, BigDecimal> out = new HashMap<>(points.size() * 2);
        for (int i = 0; i < points.size(); i++) {
            BigDecimal before = of.apply(points.get(Math.max(i - back, 0)).value());
            out.put(points.get(i).at(), of.apply(points.get(i).value()).subtract(before));
        }
        return out;
    }

    private static Map<Instant, Money> laggingGaps(CandleSeries series) {
        IchimokuCloud cloud = IchimokuCloud.standard();
        Map<Instant, Money> out = new HashMap<>(series.candles().size() * 2);
        for (int i = 0; i < series.candles().size(); i++) {
            int bar = i;
            cloud.laggingSpanGapAt(series, bar)
                    .ifPresent(gap -> out.put(series.candles().get(bar).openTime(), gap));
        }
        return out;
    }

    private static Map<Instant, String> orders(TimeframeSeries computed, CandleSeries series) {
        Map<Instant, String> out = new HashMap<>(series.candles().size() * 2);
        for (Candle candle : series.candles()) {
            for (IndicatorStance stance : computed.stancesAt(candle.openTime())) {
                if (stance.indicator() == IndicatorKind.MOVING_AVERAGE) {
                    out.put(candle.openTime(), stance.stance().name());
                }
            }
        }
        return out;
    }

    private static Map<Instant, AtrMultiple> spreads(
            TimeframeSeries computed, Map<Instant, Money> atr) {
        Map<Instant, Price> fast = line(computed, 20);
        Map<Instant, Price> slow = line(computed, 200);
        Map<Instant, AtrMultiple> out = new HashMap<>(fast.size() * 2);
        fast.forEach((at, value) -> {
            if (slow.containsKey(at) && atr.containsKey(at)) {
                out.put(at, AtrMultiple.between(value, slow.get(at), atr.get(at)));
            }
        });
        return out;
    }

    private static Map<Instant, Price> line(TimeframeSeries computed, int period) {
        return computed.movingAverages().stream()
                .filter(line -> line.period() == period)
                .findFirst()
                .map(MovingAverageLine::points)
                .map(IndicatorDumpTest::index)
                .orElse(Map.of());
    }

    private static List<String[]> cells(Path source) throws IOException {
        List<String> lines = Files.readAllLines(source);
        return lines.subList(1, lines.size()).stream().map(line -> line.split(",")).toList();
    }

    private static Candle candle(String[] cell) {
        return new Candle(
                Instant.ofEpochMilli(Long.parseLong(cell[0])),
                Price.of(new BigDecimal(cell[2])), Price.of(new BigDecimal(cell[3])),
                Price.of(new BigDecimal(cell[4])), Price.of(new BigDecimal(cell[5])),
                Quantity.of(new BigDecimal(cell[6])));
    }

    private static Map<Instant, Long> closeTimes(List<String[]> cells) {
        Map<Instant, Long> out = new HashMap<>(cells.size() * 2);
        cells.forEach(cell -> out.put(
                Instant.ofEpochMilli(Long.parseLong(cell[0])), Long.parseLong(cell[1])));
        return out;
    }

    private static List<String> prepend(List<String> rows) {
        List<String> out = new ArrayList<>(rows.size() + 1);
        out.add(HEADER);
        out.addAll(rows);
        return out;
    }
}
