package com.coinwin.trading.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.domain.Symbol;
import com.coinwin.position.domain.Direction;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * R1 · R3 · R4 · R5.
 *
 * <p><b>R4 가 이 스위트의 요점이다.</b> 절반이 확정되고 남은 절반의 최악이 0 이면 그 거래는
 * 손실로 끝날 수 없다 — "벌고 있다가 다 반납" 하는 경로가 구조적으로 사라진다.
 *
 * <p>진입가 78,000 · 손절 2% · 익절 2% · 왕복 비용 0.14% 로 고정한다. 롱 기준으로
 * 손절 76,440 · 익절 79,560 · 본전 78,109.20 이다.
 */
class ExitRuleStrategyTest {

    private static final Instant AT = Instant.parse("2026-09-18T03:00:00Z");
    private static final Money EQUITY = Money.of("800");
    private static final Price ENTRY = Price.of("78000");
    private static final ExitRuleStrategy RULES = new ExitRuleStrategy(
            Percentage.of("2"), Percentage.of("2"), Percentage.of("0.14"));
    private static final ExitRuleStrategy WITH_TRAIL = new ExitRuleStrategy(
            Percentage.of("2"), Percentage.of("2"), Percentage.of("0.14"),
            Optional.of(CallbackRate.of("1")));

    @Test
    void 열린_포지션이_없으면_아무것도_하지_않는다() {
        assertThat(RULES.decide(context(null, ENTRY, List.of())).isEmpty()).isTrue();
    }

    /** R1 — 손절이 없으면 건다. 진입가의 2% 아래인 76,440 이다. */
    @Test
    void 손절이_없으면_진입가_기준으로_건다() {
        CycleDecision decision = RULES.decide(context(Direction.LONG, ENTRY, List.of()));

        assertThat(triggersOf(decision, OrderKind.STOP_LOSS)).containsExactly(Price.of("76440.00"));
        assertThat(decision.cancel()).isEmpty();
    }

    /** R3 — 익절은 <b>절반</b>이다. 0.1 의 절반인 0.05. */
    @Test
    void 익절은_절반_수량으로_목표가에_건다() {
        CycleDecision decision = RULES.decide(context(Direction.LONG, ENTRY, List.of()));

        assertThat(decision.place()).filteredOn(i -> i.kind() == OrderKind.TAKE_PROFIT)
                .singleElement()
                .satisfies(intent -> {
                    assertThat(intent.trigger()).contains(Price.of("79560.00"));
                    assertThat(intent.quantity()).contains(Quantity.of("0.05"));
                });
    }

    /**
     * <b>표시가가 움직여도 손절이 따라가지 않는다.</b> 사이클마다 다시 계산하면 그것은
     * 손절이 아니라 의도치 않은 추격 손절이고, 불리하게 움직일 때 손절도 따라 내려간다.
     */
    @Test
    void 손절이_이미_있으면_다시_걸지_않는다() {
        CycleDecision decision = RULES.decide(context(Direction.LONG, Price.of("77000"),
                List.of(stopAt("76440.00"), takeProfitAt("79560.00"))));

        assertThat(decision.isEmpty()).isTrue();
    }

    /** <b>R4.</b> 익절 목표에 닿으면 손절이 본전으로 간다 — 78,000 의 0.14% 위. */
    @Test
    void 익절_목표에_닿으면_손절을_본전으로_옮긴다() {
        PlacedOrder old = stopAt("76440.00");

        CycleDecision decision = RULES.decide(context(Direction.LONG, Price.of("79560"),
                List.of(old, takeProfitAt("79560.00"))));

        assertThat(triggersOf(decision, OrderKind.STOP_LOSS))
                .containsExactly(Price.of("78109.20"));
        assertThat(decision.cancel()).containsExactly(old.id());
    }

    /** 본전 손절은 진입가가 아니다 — 진입가에 걸면 수수료만큼 지고 끝난다. */
    @Test
    void 본전은_진입가가_아니라_왕복_비용만큼_유리한_쪽이다() {
        CycleDecision decision = RULES.decide(context(Direction.LONG, Price.of("79560"),
                List.of(stopAt("76440.00"))));

        assertThat(triggersOf(decision, OrderKind.STOP_LOSS))
                .allSatisfy(price -> assertThat(price.isAbove(ENTRY)).isTrue());
    }

    /** 한 번 옮기고 나면 그대로 둔다. 안 그러면 사이클마다 같은 주문이 오간다. */
    @Test
    void 이미_본전이면_다시_옮기지_않는다() {
        CycleDecision decision = RULES.decide(context(Direction.LONG, Price.of("80000"),
                List.of(stopAt("78109.20"))));

        assertThat(decision.isEmpty()).isTrue();
    }

    /** 목표에 닿았다는 것은 절반이 이미 나갔다는 뜻이다. 다시 걸면 남은 절반까지 판다. */
    @Test
    void 목표에_닿은_뒤에는_익절을_다시_걸지_않는다() {
        CycleDecision decision = RULES.decide(context(Direction.LONG, Price.of("79560"),
                List.of(stopAt("78109.20"))));

        assertThat(decision.place()).noneMatch(i -> i.kind() == OrderKind.TAKE_PROFIT);
    }

    /** 숏은 부호가 전부 뒤집힌다. 손절 79,560 · 익절 76,440 · 본전 77,890.80. */
    @Test
    void 숏은_손절이_위에_익절이_아래에_놓인다() {
        CycleDecision decision = RULES.decide(context(Direction.SHORT, ENTRY, List.of()));

        assertThat(triggersOf(decision, OrderKind.STOP_LOSS)).containsExactly(Price.of("79560.00"));
        assertThat(triggersOf(decision, OrderKind.TAKE_PROFIT))
                .containsExactly(Price.of("76440.00"));
    }

    @Test
    void 숏도_목표에_닿으면_손절을_본전으로_옮긴다() {
        CycleDecision decision = RULES.decide(context(Direction.SHORT, Price.of("76440"),
                List.of(stopAt("79560.00"))));

        assertThat(triggersOf(decision, OrderKind.STOP_LOSS))
                .containsExactly(Price.of("77890.80"));
    }

    /**
     * 이미 크게 밀린 포지션에 진입가 기준으로 걸면 손절이 표시가 너머에 놓여 <b>거는 즉시
     * 체결된다</b> — 그것은 손절이 아니라 시장가 청산이고 사람이 원한 적 없는 매매다.
     */
    @Test
    void 이미_손절_거리를_지난_포지션은_표시가에서_잰다() {
        CycleDecision decision = RULES.decide(
                context(Direction.LONG, Price.of("70000"), List.of()));

        assertThat(triggersOf(decision, OrderKind.STOP_LOSS))
                .allSatisfy(price -> assertThat(price.isBelow(Price.of("70000"))).isTrue());
    }

    @Test
    void 거리는_0_보다_커야_한다() {
        assertThatThrownBy(() -> new ExitRuleStrategy(
                Percentage.of("0"), Percentage.of("2"), Percentage.of("0.14")))
                .isInstanceOf(InvalidOrderException.class);
    }

    @Test
    void 이름에_두_거리가_들어간다() {
        assertThat(RULES.name()).contains("진입 안 함").contains("손절").contains("절반 익절");
    }

    /** <b>R5.</b> 목표에 닿으면 추격 손절이 하나 더 걸린다. */
    @Test
    void 목표에_닿으면_추격_손절을_건다() {
        CycleDecision decision = WITH_TRAIL.decide(context(Direction.LONG, Price.of("79560"),
                List.of(stopAt("76440.00"))));

        assertThat(decision.place()).filteredOn(i -> i.kind() == OrderKind.TRAILING_STOP)
                .singleElement()
                .satisfies(intent -> {
                    assertThat(intent.callbackRate()).contains(CallbackRate.of("1"));
                    assertThat(intent.trigger()).isEmpty();
                    assertThat(intent.quantity()).contains(Quantity.of("0.1"));
                });
    }

    /**
     * <b>R5 는 R4 를 대신하지 않는다.</b> 같은 사이클에 본전 손절도 함께 걸린다 —
     * 추격이 본전보다 아래에 놓이는 폭이면 그 거래가 다시 손실로 끝날 수 있게 된다.
     */
    @Test
    void 추격을_걸어도_본전_손절이_함께_남는다() {
        CycleDecision decision = WITH_TRAIL.decide(context(Direction.LONG, Price.of("79560"),
                List.of(stopAt("76440.00"))));

        assertThat(triggersOf(decision, OrderKind.STOP_LOSS))
                .containsExactly(Price.of("78109.20"));
    }

    /** 목표에 닿기 전에는 추격을 걸지 않는다. 남은 절반이 아직 안 생겼다. */
    @Test
    void 목표에_닿기_전에는_추격을_걸지_않는다() {
        CycleDecision decision = WITH_TRAIL.decide(context(Direction.LONG, ENTRY, List.of()));

        assertThat(decision.place()).noneMatch(i -> i.kind() == OrderKind.TRAILING_STOP);
    }

    /** 이미 걸려 있으면 다시 걸지 않는다. 안 그러면 사이클마다 추격이 쌓인다. */
    @Test
    void 추격이_이미_걸려_있으면_다시_걸지_않는다() {
        CycleDecision decision = WITH_TRAIL.decide(context(Direction.LONG, Price.of("80000"),
                List.of(stopAt("78109.20"), trailingOrder())));

        assertThat(decision.isEmpty()).isTrue();
    }

    /** <b>폭을 안 주면 R5 를 하지 않는다.</b> 거래소 허용 범위를 확인 못 했으니 끌 수 있어야 한다. */
    @Test
    void 추격_폭이_없으면_R5_를_하지_않는다() {
        CycleDecision decision = RULES.decide(context(Direction.LONG, Price.of("79560"),
                List.of(stopAt("76440.00"))));

        assertThat(decision.place()).noneMatch(i -> i.kind() == OrderKind.TRAILING_STOP);
    }

    @Test
    void 이름에_추격_폭이_들어간다() {
        assertThat(WITH_TRAIL.name()).contains("추격 1.0%");
        assertThat(RULES.name()).doesNotContain("추격");
    }

    private static List<Price> triggersOf(CycleDecision decision, OrderKind kind) {
        return decision.place().stream()
                .filter(intent -> intent.kind() == kind)
                .map(intent -> intent.trigger().orElseThrow())
                .toList();
    }

    private static BotContext context(Direction open, Price mark, List<PlacedOrder> resting) {
        return new BotContext(
                new MarketView(Symbol.BTC_USDT, mark, List.of(), AT),
                new AccountState(EQUITY, open == null ? 0 : 1, Money.of("0"), Money.of("0")),
                open == null
                        ? Optional.empty()
                        : Optional.of(new BotPosition(open, Quantity.of("0.1"), ENTRY)),
                resting);
    }

    private static PlacedOrder stopAt(String trigger) {
        return resting(OrderKind.STOP_LOSS, trigger, "stop-" + trigger);
    }

    private static PlacedOrder takeProfitAt(String trigger) {
        return resting(OrderKind.TAKE_PROFIT, trigger, "tp-" + trigger);
    }

    private static PlacedOrder trailingOrder() {
        return new PlacedOrder(new OrderId("trail"),
                OrderIntent.trail(Symbol.BTC_USDT, Direction.LONG, new OrderIntent.Trailing(
                        CallbackRate.of("1"), Quantity.of("0.1"), ENTRY)),
                Optional.empty(), AT, TradingMode.PAPER);
    }

    private static PlacedOrder resting(OrderKind kind, String trigger, String id) {
        return new PlacedOrder(new OrderId(id),
                OrderIntent.protectAll(Symbol.BTC_USDT, Direction.LONG,
                        new OrderIntent.Protection(kind, Price.of(trigger), ENTRY)),
                Optional.empty(), AT, TradingMode.PAPER);
    }
}
