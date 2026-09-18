package com.coinwin.trading.adapter.out.paper;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.backtest.domain.CostModel;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.domain.Symbol;
import com.coinwin.position.domain.Direction;
import com.coinwin.trading.domain.OrderIntent;
import com.coinwin.trading.domain.OrderKind;
import com.coinwin.trading.domain.PlacedOrder;
import com.coinwin.trading.domain.TradingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 장부 브로커. <b>봇의 기본 모드이고 돈이 들지 않는다.</b>
 *
 * <p>슬리피지 1% 로 고정해 부호를 눈으로 확인할 수 있게 한다 — 실제 기본값(0.02%)으로는
 * 78,000 이 77,984.40 이 되어 방향이 맞는지 한눈에 안 보인다.
 */
class PaperBrokerAdapterTest {

    private static final Instant AT = Instant.parse("2026-09-18T00:00:00Z");
    private static final Money EQUITY = Money.of("800");
    private static final CostModel COSTS = new CostModel(
            Percentage.of("0.02"), Percentage.of("0.05"), Percentage.of("1"));

    private final PaperBrokerAdapter broker =
            new PaperBrokerAdapter(COSTS, Clock.fixed(AT, ZoneOffset.UTC), EQUITY);

    @Test
    void 장부_모드로_낸다() {
        assertThat(broker.mode()).isEqualTo(TradingMode.PAPER);
        assertThat(broker.mode().movesRealMoney()).isFalse();
    }

    /**
     * <b>롱을 여는 것은 사는 것이라 더 비싸게 체결된다.</b> 78,000 의 1% 위인 78,780 이다.
     * 부호를 뒤집으면 장부가 실제보다 좋게 나온다.
     */
    @Test
    void 롱_진입은_불리하게_더_비싸게_체결된다() {
        assertThat(broker.place(entry(Direction.LONG)).fillPrice())
                .contains(Price.of("78780.00"));
    }

    @Test
    void 숏_진입은_불리하게_더_싸게_체결된다() {
        assertThat(broker.place(entry(Direction.SHORT)).fillPrice())
                .contains(Price.of("77220.00"));
    }

    /** 진입이 장부에 쌓여야 동시 포지션 한계가 일을 한다. */
    @Test
    void 진입하면_장부에_포지션이_생긴다() {
        broker.place(entry(Direction.LONG));

        assertThat(broker.ledger().position()).isPresent();
        assertThat(broker.ledger().state().openPositions()).isEqualTo(1);
    }

    /**
     * <b>이것이 이 클래스의 존재 이유다.</b> 손절이 걸리기만 하고 영영 체결되지 않으면
     * 모의 기록은 지는 거래가 하나도 없는 거짓 장부가 된다.
     */
    @Test
    void 표시가가_손절에_닿으면_체결된다() {
        broker.place(entry(Direction.LONG));
        broker.place(stop(Direction.LONG, "77000"));

        broker.advanceTo(Price.of("76900"));

        assertThat(broker.ledger().position()).isEmpty();
        assertThat(broker.ledger().state().lostTotal().value().signum()).isPositive();
    }

    @Test
    void 표시가가_트리거에_닿지_않으면_그대로_걸려_있다() {
        broker.place(entry(Direction.LONG));
        broker.place(stop(Direction.LONG, "77000"));

        broker.advanceTo(Price.of("77500"));

        assertThat(broker.ledger().position()).isPresent();
        assertThat(broker.restingOrders()).hasSize(1);
    }

    /** 숏의 손절은 <b>위로</b> 닿는다. 부호를 뒤집으면 손절이 이익 구간에서 터진다. */
    @Test
    void 숏의_손절은_가격이_오를_때_닿는다() {
        broker.place(entry(Direction.SHORT));
        broker.place(stop(Direction.SHORT, "79000"));

        broker.advanceTo(Price.of("79100"));

        assertThat(broker.ledger().position()).isEmpty();
    }

    /** 안 지우면 다음 진입이 앞 거래의 손절을 물려받는다. */
    @Test
    void 포지션이_닫히면_남은_트리거를_지운다() {
        broker.place(entry(Direction.LONG));
        broker.place(stop(Direction.LONG, "77000"));
        broker.place(takeProfit(Direction.LONG, "80000"));

        broker.advanceTo(Price.of("76900"));

        assertThat(broker.restingOrders()).isEmpty();
    }

    /** 익절이 닿으면 실현 손익이 양수가 된다. */
    @Test
    void 익절에_닿으면_이익이_실현된다() {
        broker.place(entry(Direction.LONG));
        broker.place(takeProfit(Direction.LONG, "82000"));

        broker.advanceTo(Price.of("82100"));

        assertThat(broker.ledger().realizedTotal().isNegative()).isFalse();
        assertThat(broker.ledger().state().equity().isGreaterThan(EQUITY)).isTrue();
    }

    @Test
    void 트리거_주문은_체결되지_않고_걸려_있다() {
        PlacedOrder placed = broker.place(stop(Direction.LONG, "77000"));

        assertThat(placed.resting()).isTrue();
        assertThat(broker.restingOrders()).containsExactly(placed);
    }

    @Test
    void 취소하면_장부에서_사라진다() {
        PlacedOrder placed = broker.place(stop(Direction.LONG, "77000"));

        broker.cancel(placed.id());

        assertThat(broker.restingOrders()).isEmpty();
    }

    /** 이미 없는 주문을 지우는 것은 실패가 아니다 — 원하던 상태가 이미 됐다. */
    @Test
    void 없는_주문을_취소해도_던지지_않는다() {
        PlacedOrder placed = broker.place(stop(Direction.LONG, "77000"));
        broker.cancel(placed.id());

        broker.cancel(placed.id());

        assertThat(broker.restingOrders()).isEmpty();
    }

    @Test
    void 주문마다_다른_식별자를_준다() {
        assertThat(broker.place(stop(Direction.LONG, "77000")).id())
                .isNotEqualTo(broker.place(stop(Direction.LONG, "76000")).id());
    }

    /** 열린 것이 없는데 닫는 주문이 나가는 것은 실패가 아니라 이미 원하던 상태다. */
    @Test
    void 포지션이_없을_때_닫아도_던지지_않는다() {
        OrderIntent exit = new OrderIntent(Symbol.BTC_USDT, Direction.LONG, OrderKind.EXIT,
                Optional.empty(), Optional.empty(), Price.of("78000"));

        assertThat(broker.place(exit).fillPrice()).isPresent();
        assertThat(broker.ledger().position()).isEmpty();
    }

    private static OrderIntent entry(Direction direction) {
        return OrderIntent.entry(Symbol.BTC_USDT, direction,
                new OrderIntent.Sizing(Quantity.of("0.01"), Price.of("78000")));
    }

    private static OrderIntent stop(Direction direction, String trigger) {
        return OrderIntent.protectAll(Symbol.BTC_USDT, direction,
                new OrderIntent.Protection(
                        OrderKind.STOP_LOSS, Price.of(trigger), Price.of("78000")));
    }

    private static OrderIntent takeProfit(Direction direction, String trigger) {
        return OrderIntent.protectAll(Symbol.BTC_USDT, direction,
                new OrderIntent.Protection(
                        OrderKind.TAKE_PROFIT, Price.of(trigger), Price.of("78000")));
    }
}
