package com.coinwin.backtest.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.backtest.BacktestFixtures;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.indicator.domain.BandPosition;
import com.coinwin.journal.domain.ClosedTrade;
import com.coinwin.journal.domain.ExitReason;
import com.coinwin.journal.domain.MarketContext;
import com.coinwin.market.domain.Candle;
import com.coinwin.position.domain.Direction;
import com.coinwin.position.domain.EntryLadder;
import com.coinwin.position.domain.PlannedEntry;
import com.coinwin.position.domain.PositionPlan;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 봉 안에서 일어나는 일 전부.
 *
 * <p>엔진을 통하지 않고 여기서 검사하는 이유는 일목 워밍업 77봉 때문이다. 엔진으로 이 규칙들을
 * 확인하려면 매번 80봉짜리 시나리오를 조립해야 하고, 그러면 무엇을 검사하는 테스트인지
 * 읽히지 않는다.
 */
class OpenPositionTest {

    /** 롱: 진입 59200 / 59000, 손절 58900, 익절 62000. */
    private static final PositionPlan LONG_PLAN = new PositionPlan(
            Direction.LONG,
            EntryLadder.of(PlannedEntry.of("59200", "50"), PlannedEntry.of("59000", "50")),
            Price.of("58900"), Price.of("62000"), 10);

    private static final MarketContext CONTEXT = new MarketContext(
            Price.of("60000"), BandPosition.INSIDE, BandPosition.INSIDE, "지지대 반전");

    private static final Quantity TOTAL = Quantity.of("0.1");
    private static final CostModel FREE = CostModel.free();
    private static final CostModel CHARGED = new CostModel(
            Percentage.of("0.02"), Percentage.of("0.05"), Percentage.of("0.02"));

    private static ArmedOrder order(PositionPlan plan) {
        return new ArmedOrder(new TradeSignal(plan.direction(), plan, CONTEXT), TOTAL,
                BacktestFixtures.hour(0));
    }

    /** 1차만 체결된 포지션. */
    private static OpenPosition halfFilled() {
        return OpenPosition.opened(order(LONG_PLAN), Price.of("59200"),
                BacktestFixtures.hour(1), FREE);
    }

    private static Candle bar(String ohlc) {
        return BacktestFixtures.ohlc(2, ohlc);
    }

    // ── 봉 내부 순서 ────────────────────────────────────────────────────────

    /**
     * 손절과 익절이 같은 봉에서 모두 닿으면 <b>손절</b>이다. 인터뷰에서 확정한 규칙이고,
     * "보유자에게 불리한 순서" 원칙의 한 사례다.
     */
    @Test
    void 같은_봉에서_손절과_익절이_모두_닿으면_손절로_처리한다() {
        ClosedTrade closed = halfFilled().closeIn(bar("60000/62500/58500/61000"), FREE)
                .orElseThrow();

        assertThat(closed.closure().reason()).isEqualTo(ExitReason.PLANNED_STOP);
        assertThat(closed.closure().exit().price()).isEqualTo(Price.of("58900"));
    }

    @Test
    void 익절만_닿으면_익절이다() {
        ClosedTrade closed = halfFilled().closeIn(bar("60000/62500/59500/62200"), FREE)
                .orElseThrow();

        assertThat(closed.closure().reason()).isEqualTo(ExitReason.PLANNED_TARGET);
        assertThat(closed.closure().exit().price()).isEqualTo(Price.of("62000"));
    }

    @Test
    void 둘_다_닿지_않으면_포지션이_유지된다() {
        assertThat(halfFilled().closeIn(bar("60000/61000/59500/60500"), FREE)).isEmpty();
    }

    /** 손절가를 뛰어넘어 열린 봉의 체결가는 손절가가 아니라 시가다. */
    @Test
    void 갭으로_손절가를_뛰어넘어_열리면_체결가는_시가다() {
        ClosedTrade closed = halfFilled().closeIn(bar("58000/58200/57500/58100"), FREE)
                .orElseThrow();

        assertThat(closed.closure().exit().price()).isEqualTo(Price.of("58000"));
    }

    // ── 부분 체결 ───────────────────────────────────────────────────────────

    @Test
    void 일차만_체결된_상태의_수량은_총수량의_절반이다() {
        assertThat(halfFilled().fills()).singleElement()
                .satisfies(fill -> assertThat(fill.quantity()).isEqualTo(Quantity.of("0.05")));
        assertThat(halfFilled().fullyFilled()).isFalse();
    }

    /** 2차 지정가는 포지션이 살아 있는 동안 계속 유효하다. */
    @Test
    void 이차_지정가에_닿으면_추가로_체결된다() {
        Candle candle = bar("59150/59250/58950/59100");

        Optional<Price> fill = halfFilled().nextEntryFill(candle);

        assertThat(fill).contains(Price.of("59000"));
    }

    @Test
    void 전량_체결된_뒤에는_추가_체결이_없다() {
        OpenPosition full = halfFilled()
                .withNextFill(Price.of("59000"), BacktestFixtures.hour(2), FREE);

        assertThat(full.fullyFilled()).isTrue();
        assertThat(full.nextEntryFill(bar("59000/59100/58950/59050"))).isEmpty();
    }

    /**
     * 2차가 체결된 뒤 손절되면 손실은 <b>늘어난 수량 기준</b>이다. 부분 체결 상태로 손절된
     * 것보다 나쁘고, 그것이 분할 진입의 실제 위험이다.
     */
    @Test
    void 이차까지_체결된_뒤_손절되면_손실이_더_크다() {
        Candle wide = bar("59150/59200/58500/58600");

        ClosedTrade partial = halfFilled().closeIn(wide, FREE).orElseThrow();
        ClosedTrade full = halfFilled().withNextFill(Price.of("59000"), BacktestFixtures.hour(2),
                FREE).closeIn(wide, FREE).orElseThrow();

        assertThat(full.realizedPnl().value()).isLessThan(partial.realizedPnl().value());
        assertThat(full.entries().totalQuantity()).isEqualTo(Quantity.of("0.1"));
    }

    // ── 비용 ────────────────────────────────────────────────────────────────

    /** 슬리피지는 롱 청산가를 낮춘다. 비용 모델을 켠 결과가 끈 결과보다 나쁠 수밖에 없다. */
    @Test
    void 비용을_켜면_같은_봉에서_손익이_나빠진다() {
        Candle candle = bar("60000/62500/59500/62200");

        ClosedTrade free = halfFilled().closeIn(candle, FREE).orElseThrow();
        OpenPosition charged = OpenPosition.opened(order(LONG_PLAN), Price.of("59200"),
                BacktestFixtures.hour(1), CHARGED);
        ClosedTrade paid = charged.closeIn(candle, CHARGED).orElseThrow();

        assertThat(paid.closure().exit().price()).isEqualTo(Price.of("61987.60"));
        assertThat(paid.realizedPnl().value()).isLessThan(free.realizedPnl().value());
        assertThat(free.closure().costs().total()).isEqualTo(Money.of("0"));
    }

    /** 펀딩비는 모델에 없다. 0 으로 <b>드러나게</b> 두어 한계가 결과에 보이게 한다. */
    @Test
    void 펀딩비는_0으로_남는다() {
        ClosedTrade closed = halfFilled()
                .closeIn(bar("60000/62500/59500/62200"), CHARGED).orElseThrow();

        assertThat(closed.closure().costs().funding()).isEqualTo(Money.of("0"));
        assertThat(closed.closure().costs().fees().value().signum()).isPositive();
    }

    // ── 결정론 ──────────────────────────────────────────────────────────────

    /**
     * 식별자는 진입 시각에서 유도한다. {@code TradeId.random()} 이면 같은 스펙을 두 번 돌린
     * 결과가 서로 달라 완료 조건이 그 자리에서 무너진다.
     */
    @Test
    void 같은_진입_시각이면_같은_식별자가_나온다() {
        Candle candle = bar("60000/62500/59500/62200");

        assertThat(halfFilled().closeIn(candle, FREE).orElseThrow().id())
                .isEqualTo(halfFilled().closeIn(candle, FREE).orElseThrow().id());
    }

    // ── 숏 ──────────────────────────────────────────────────────────────────

    @Test
    void 숏은_상승해서_손절되고_하락해서_익절된다() {
        PositionPlan shortPlan = new PositionPlan(
                Direction.SHORT,
                EntryLadder.of(PlannedEntry.of("62000", "50"), PlannedEntry.of("62200", "50")),
                Price.of("62300"), Price.of("59200"), 10);
        OpenPosition position = OpenPosition.opened(order(shortPlan), Price.of("62000"),
                BacktestFixtures.hour(1), FREE);

        assertThat(position.closeIn(bar("62100/62400/62050/62350"), FREE).orElseThrow()
                .closure().reason()).isEqualTo(ExitReason.PLANNED_STOP);
        assertThat(position.closeIn(bar("61000/61100/59000/59100"), FREE).orElseThrow()
                .closure().reason()).isEqualTo(ExitReason.PLANNED_TARGET);
    }
}
