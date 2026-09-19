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
 * 규칙 R1 — <b>손절 없는 포지션은 존재할 수 없다.</b>
 *
 * <p>진입 규칙이 없는 지금 꽂을 수 있는 유일한 전략이다. 새 수익을 만들지 않고 왼쪽 꼬리만
 * 자르므로 엣지를 요구하지 않는다.
 */
class StopLossGuardStrategyTest {

    private static final Instant AT = Instant.parse("2026-09-18T03:00:00Z");
    private static final Money EQUITY = Money.of("800");
    private static final StopLossGuardStrategy GUARD =
            new StopLossGuardStrategy(Percentage.of("2"));

    @Test
    void 열린_포지션이_없으면_아무것도_하지_않는다() {
        assertThat(GUARD.decide(context(null, List.of())).isEmpty()).isTrue();
    }

    /** 롱의 손절은 <b>아래</b>에 놓인다. 78,000 의 2% 아래인 76,440 이다. */
    @Test
    void 손절이_없는_롱에_아래쪽_손절을_건다() {
        List<OrderIntent> intents = GUARD.decide(context(Direction.LONG, List.of())).place();

        assertThat(intents).singleElement().satisfies(intent -> {
            assertThat(intent.kind()).isEqualTo(OrderKind.STOP_LOSS);
            assertThat(intent.trigger()).contains(Price.of("76440.00"));
            assertThat(intent.closesEntirePosition()).isTrue();
        });
    }

    /** 숏의 손절은 <b>위</b>에 놓인다. 부호를 뒤집으면 손절이 이익 구간에서 터진다. */
    @Test
    void 손절이_없는_숏에_위쪽_손절을_건다() {
        assertThat(GUARD.decide(context(Direction.SHORT, List.of())).place())
                .singleElement()
                .satisfies(intent -> assertThat(intent.trigger()).contains(Price.of("79560.00")));
    }

    /** 이미 있으면 다시 걸지 않는다. 안 그러면 사이클마다 손절이 쌓인다. */
    @Test
    void 손절이_이미_걸려_있으면_다시_걸지_않는다() {
        assertThat(GUARD.decide(context(Direction.LONG, List.of(restingStop(Direction.LONG))))
                .isEmpty()).isTrue();
    }

    /**
     * <b>이것이 R2 다.</b> 사람이 거래소 화면에서 손절을 지우면 다음 사이클에 다시 걸린다 —
     * 지우는 것이 능동적 행동이 되고 그 행동은 몇 초 뒤 되돌려진다.
     */
    @Test
    void 손절이_사라지면_다시_건다() {
        BotContext withStop = context(Direction.LONG, List.of(restingStop(Direction.LONG)));
        BotContext afterDeleted = context(Direction.LONG, List.of());

        assertThat(GUARD.decide(withStop).isEmpty()).isTrue();
        assertThat(GUARD.decide(afterDeleted).place()).hasSize(1);
    }

    /** 반대 방향의 손절은 이 포지션을 덮지 않는다. 세면 보호되지 않은 것이 보호된 것으로 보인다. */
    @Test
    void 반대_방향의_손절은_이_포지션을_덮지_않는다() {
        assertThat(GUARD.decide(context(Direction.LONG, List.of(restingStop(Direction.SHORT))))
                .place()).hasSize(1);
    }

    /** 익절은 손절이 아니다. § 0 의 실패 모드가 정확히 이 모양이다. */
    @Test
    void 익절만_걸려_있으면_손절을_건다() {
        assertThat(GUARD.decide(context(Direction.LONG, List.of(restingTakeProfit()))).place())
                .hasSize(1);
    }

    @Test
    void 손절_거리는_0_보다_커야_한다() {
        assertThatThrownBy(() -> new StopLossGuardStrategy(Percentage.of("0")))
                .isInstanceOf(InvalidOrderException.class);
    }

    @Test
    void 이름에_거리가_들어간다() {
        assertThat(GUARD.name()).contains("2").contains("진입 안 함");
    }

    private static BotContext context(Direction open, List<PlacedOrder> resting) {
        return new BotContext(
                new MarketView(Symbol.BTC_USDT, Price.of("78000"), List.of(), AT),
                MarketReading.none(),
                new AccountState(EQUITY, open == null ? 0 : 1, Money.of("0"), Money.of("0")),
                open == null
                        ? Optional.empty()
                        : Optional.of(new BotPosition(
                                open, Quantity.of("0.1"), Price.of("77000"))),
                resting);
    }

    private static PlacedOrder restingStop(Direction closes) {
        return new PlacedOrder(new OrderId("x"),
                OrderIntent.protectAll(Symbol.BTC_USDT, closes, new OrderIntent.Protection(
                        OrderKind.STOP_LOSS, Price.of("76000"), Price.of("78000"))),
                Optional.empty(), AT, TradingMode.PAPER);
    }

    private static PlacedOrder restingTakeProfit() {
        return new PlacedOrder(new OrderId("y"),
                OrderIntent.protectAll(Symbol.BTC_USDT, Direction.LONG,
                        new OrderIntent.Protection(
                                OrderKind.TAKE_PROFIT, Price.of("82000"), Price.of("78000"))),
                Optional.empty(), AT, TradingMode.PAPER);
    }
}
