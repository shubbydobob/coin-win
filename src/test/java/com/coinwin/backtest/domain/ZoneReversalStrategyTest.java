package com.coinwin.backtest.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.backtest.BacktestFixtures;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import com.coinwin.indicator.domain.BandPosition;
import com.coinwin.position.domain.Direction;
import com.coinwin.position.domain.PositionPlan;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ZoneReversalStrategyTest {

    private static final int LEVERAGE = 10;
    private static final Money ATR = Money.of("100");

    /** 필터를 끈 규칙. 대 논리만 볼 때 쓴다. */
    private static final ZoneReversalStrategy NO_FILTER = new ZoneReversalStrategy(
            new EntryRules(new BigDecimal("1"), new BigDecimal("1.5"), false));

    private static final IndicatorReading NEUTRAL =
            new IndicatorReading(BandPosition.INSIDE, BandPosition.INSIDE);

    private static Pivot pivot(int index, String price) {
        return new Pivot(BacktestFixtures.hour(index), BacktestFixtures.hour(index + 2),
                Price.of(price), PivotKind.SWING_HIGH);
    }

    /** 대 둘: 아래 {@code 59000~59200}, 위 {@code 62000~62200}. */
    private static ZoneMap 위아래_대() {
        return ZoneMap.from(List.of(
                pivot(0, "59000"), pivot(1, "59200"),
                pivot(2, "62000"), pivot(3, "62200")), Money.of("300"), 2);
    }

    private static MarketSnapshot 종가(String close) {
        return new MarketSnapshot(BacktestFixtures.hour(10), Price.of(close), ATR, 위아래_대());
    }

    private static Optional<TradeSignal> 신호(String close) {
        return NO_FILTER.signalAt(종가(close), NEUTRAL, LEVERAGE);
    }

    // ── 방향과 가격 배치 ────────────────────────────────────────────────────

    /**
     * 종가 60000 은 아래 대(59200)까지 800, 위 대(62000)까지 2000. 아래가 가깝다.
     *
     * <p>1차는 근단 59200, 2차는 원단 59000, 손절은 원단 − ATR×1 = 58900,
     * 익절은 반대편 대의 근단 62000.
     */
    @Test
    void 종가에_더_가까운_지지대에_롱을_무장한다() {
        TradeSignal signal = 신호("60000").orElseThrow();
        PositionPlan plan = signal.plan();

        assertThat(signal.direction()).isEqualTo(Direction.LONG);
        assertThat(plan.entries().highestPrice()).isEqualTo(Price.of("59200"));
        assertThat(plan.entries().lowestPrice()).isEqualTo(Price.of("59000"));
        assertThat(plan.stopLoss()).isEqualTo(Price.of("58900"));
        assertThat(plan.takeProfit()).isEqualTo(Price.of("62000"));
        assertThat(plan.leverage()).isEqualTo(LEVERAGE);
    }

    /** 종가 61500 은 위 대까지 500, 아래 대까지 2300. 숏이고 근단·원단이 뒤집힌다. */
    @Test
    void 종가에_더_가까운_저항대에_숏을_무장한다() {
        TradeSignal signal = 신호("61500").orElseThrow();
        PositionPlan plan = signal.plan();

        assertThat(signal.direction()).isEqualTo(Direction.SHORT);
        assertThat(plan.entries().lowestPrice()).isEqualTo(Price.of("62000"));
        assertThat(plan.entries().highestPrice()).isEqualTo(Price.of("62200"));
        assertThat(plan.stopLoss()).isEqualTo(Price.of("62300"));
        assertThat(plan.takeProfit()).isEqualTo(Price.of("59200"));
    }

    @Test
    void 분할은_50대50이고_순서는_근단_먼저다() {
        var entries = 신호("60000").orElseThrow().plan().entries().entries();

        assertThat(entries).extracting(entry -> entry.price().value().toPlainString())
                .containsExactly("59200.00", "59000.00");
        assertThat(entries).allSatisfy(entry ->
                assertThat(entry.allocation().value()).isEqualByComparingTo("50"));
    }

    /**
     * 거리가 정확히 같으면 고를 근거가 없다.
     *
     * <p>초안 명세는 "롱·숏 후보가 동시에 서면 둘 다 버린다" 였는데, 그것은 거의 모든 봉에
     * 해당해 거래를 전부 없앤다. 실제로 근거가 없는 것은 <b>동률</b>일 때뿐이다.
     */
    @Test
    void 양쪽_대까지의_거리가_같으면_버린다() {
        // 59200 에서 1400, 62000 에서 1400
        assertThat(신호("60600")).isEmpty();
    }

    // ── 게이트 ──────────────────────────────────────────────────────────────

    /** 대 안에서는 어느 경계가 근단인지 정해지지 않는다. */
    @Test
    void 가격이_대_안에_있으면_신호가_없다() {
        assertThat(신호("59100")).isEmpty();
        assertThat(신호("59000")).isEmpty();
        assertThat(신호("59200")).isEmpty();
    }

    /** 익절가를 임의의 R 배수로 대신하지 않는다. 규칙이 둘이 되면 결과 원인을 가를 수 없다. */
    @Test
    void 반대편_대가_없으면_신호가_없다() {
        ZoneMap 아래만 = ZoneMap.from(
                List.of(pivot(0, "59000"), pivot(1, "59200")), Money.of("300"), 2);
        MarketSnapshot snapshot =
                new MarketSnapshot(BacktestFixtures.hour(10), Price.of("60000"), ATR, 아래만);

        assertThat(NO_FILTER.signalAt(snapshot, NEUTRAL, LEVERAGE)).isEmpty();
    }

    @Test
    void 대가_하나도_없으면_신호가_없다() {
        MarketSnapshot snapshot = new MarketSnapshot(
                BacktestFixtures.hour(10), Price.of("60000"), ATR, ZoneMap.from(
                        List.of(), Money.of("300"), 2));

        assertThat(NO_FILTER.signalAt(snapshot, NEUTRAL, LEVERAGE)).isEmpty();
    }

    /**
     * 손절 버퍼가 0 이면 손절가와 최저 진입가가 같아진다 — {@code PositionPlan} 이 거부한다.
     * 그 거부는 예외가 아니라 "신호 없음" 으로 흡수한다. 백테스트가 중간에 멈추면 안 된다.
     */
    @Test
    void 계획이_성립하지_않으면_예외가_아니라_신호_없음이다() {
        ZoneReversalStrategy 버퍼_없음 = new ZoneReversalStrategy(
                new EntryRules(BigDecimal.ZERO, new BigDecimal("1.5"), false));

        assertThat(버퍼_없음.signalAt(종가("60000"), NEUTRAL, LEVERAGE)).isEmpty();
    }

    /**
     * 롱은 손절 300(59200 평단 기준이 아니라 평단 59100 에서 58900 까지 200),
     * 익절 62000 까지 2900 → 손익비가 충분하다. 문턱을 그 위로 올리면 걸러진다.
     */
    @Test
    void 손익비가_문턱값에_못_미치면_버린다() {
        ZoneReversalStrategy 높은_문턱 = new ZoneReversalStrategy(
                new EntryRules(new BigDecimal("1"), new BigDecimal("100"), false));

        assertThat(높은_문턱.signalAt(종가("60000"), NEUTRAL, LEVERAGE)).isEmpty();
        assertThat(NO_FILTER.signalAt(종가("60000"), NEUTRAL, LEVERAGE)).isPresent();
    }

    // ── 지표 필터 ───────────────────────────────────────────────────────────

    private static final ZoneReversalStrategy WITH_FILTER = new ZoneReversalStrategy(
            new EntryRules(new BigDecimal("1"), new BigDecimal("1.5"), true));

    @Test
    void 구름_아래에서는_롱을_걸러_내고_구름_위에서는_숏을_걸러_낸다() {
        assertThat(WITH_FILTER.signalAt(종가("60000"),
                new IndicatorReading(BandPosition.BELOW, BandPosition.INSIDE), LEVERAGE)).isEmpty();
        assertThat(WITH_FILTER.signalAt(종가("61500"),
                new IndicatorReading(BandPosition.ABOVE, BandPosition.INSIDE), LEVERAGE)).isEmpty();
    }

    @Test
    void 볼린저_상단_밖에서는_롱을_걸러_내고_하단_밖에서는_숏을_걸러_낸다() {
        assertThat(WITH_FILTER.signalAt(종가("60000"),
                new IndicatorReading(BandPosition.INSIDE, BandPosition.ABOVE), LEVERAGE)).isEmpty();
        assertThat(WITH_FILTER.signalAt(종가("61500"),
                new IndicatorReading(BandPosition.INSIDE, BandPosition.BELOW), LEVERAGE)).isEmpty();
    }

    @Test
    void 필터가_동의하면_통과한다() {
        assertThat(WITH_FILTER.signalAt(종가("60000"),
                new IndicatorReading(BandPosition.ABOVE, BandPosition.BELOW), LEVERAGE)).isPresent();
    }

    /** 같은 입력에서 필터만 끄면 통과한다 — 온오프 비교가 성립하려면 이것이 유일한 차이여야 한다. */
    @Test
    void 필터를_끄면_같은_입력이_통과한다() {
        IndicatorReading 롱에_불리 = new IndicatorReading(BandPosition.BELOW, BandPosition.ABOVE);

        assertThat(WITH_FILTER.signalAt(종가("60000"), 롱에_불리, LEVERAGE)).isEmpty();
        assertThat(NO_FILTER.signalAt(종가("60000"), 롱에_불리, LEVERAGE)).isPresent();
    }

    // ── 기록 ────────────────────────────────────────────────────────────────

    /**
     * 필터를 껐어도 지표 판정은 기록한다. 온오프 비교의 대상이 되려면 두 실행이 같은 값을
     * 남겨야 한다.
     */
    @Test
    void 지표_판정은_필터_여부와_무관하게_기록된다() {
        IndicatorReading reading = new IndicatorReading(BandPosition.ABOVE, BandPosition.BELOW);

        var context = NO_FILTER.signalAt(종가("60000"), reading, LEVERAGE).orElseThrow().context();

        assertThat(context.priceAtEntry()).isEqualTo(Price.of("60000"));
        assertThat(context.ichimokuPosition()).isEqualTo(BandPosition.ABOVE);
        assertThat(context.bollingerPosition()).isEqualTo(BandPosition.BELOW);
    }

    /** 근거는 비어 있으면 거부된다(ADR 017). 백테스트는 대의 정체를 문장으로 남긴다. */
    @Test
    void 근거에는_대의_구간과_터치_횟수가_들어간다() {
        String rationale = 신호("60000").orElseThrow().context().rationale();

        assertThat(rationale).contains("지지").contains("59000").contains("59200").contains("2");
    }
}
