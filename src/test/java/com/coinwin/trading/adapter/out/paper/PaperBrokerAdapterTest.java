package com.coinwin.trading.adapter.out.paper;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.backtest.domain.CostModel;
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
import org.junit.jupiter.api.Test;

/**
 * 장부 브로커. <b>봇의 기본 모드이고 돈이 들지 않는다.</b>
 *
 * <p>슬리피지 1% 로 고정해 부호를 눈으로 확인할 수 있게 한다 — 실제 기본값(0.02%)으로는
 * 78,000 이 77,984.40 이 되어 방향이 맞는지 한눈에 안 보인다.
 */
class PaperBrokerAdapterTest {

    private static final Instant AT = Instant.parse("2026-09-18T00:00:00Z");
    private static final CostModel COSTS = new CostModel(
            Percentage.of("0.02"), Percentage.of("0.05"), Percentage.of("1"));

    private final PaperBrokerAdapter broker =
            new PaperBrokerAdapter(COSTS, Clock.fixed(AT, ZoneOffset.UTC));

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
        PlacedOrder placed = broker.place(entry(Direction.LONG));

        assertThat(placed.fillPrice()).contains(Price.of("78780.00"));
    }

    /** 숏을 여는 것은 파는 것이라 더 싸게 체결된다. 78,000 의 1% 아래인 77,220 이다. */
    @Test
    void 숏_진입은_불리하게_더_싸게_체결된다() {
        assertThat(broker.place(entry(Direction.SHORT)).fillPrice())
                .contains(Price.of("77220.00"));
    }

    /** 롱을 닫는 것은 파는 것이다 — 여는 쪽과 부호가 반대다. */
    @Test
    void 롱_청산은_불리하게_더_싸게_체결된다() {
        OrderIntent exit = new OrderIntent(Symbol.BTC_USDT, Direction.LONG, OrderKind.EXIT,
                java.util.Optional.empty(), java.util.Optional.empty(), Price.of("78000"));

        assertThat(broker.place(exit).fillPrice()).contains(Price.of("77220.00"));
    }

    /**
     * 트리거 주문은 걸려만 있다. <b>0 으로 채우면 "0 원에 체결됐다" 가 되고</b> 그 수가
     * 손익 계산에 그대로 들어간다.
     */
    @Test
    void 트리거_주문은_체결되지_않고_걸려_있다() {
        PlacedOrder placed = broker.place(stop());

        assertThat(placed.resting()).isTrue();
        assertThat(placed.fillPrice()).isEmpty();
        assertThat(broker.restingOrders()).containsExactly(placed);
    }

    @Test
    void 시장가_주문은_걸려_있지_않는다() {
        broker.place(entry(Direction.LONG));

        assertThat(broker.restingOrders()).isEmpty();
    }

    @Test
    void 취소하면_장부에서_사라진다() {
        PlacedOrder placed = broker.place(stop());

        broker.cancel(placed.id());

        assertThat(broker.restingOrders()).isEmpty();
    }

    /** 이미 없는 주문을 지우는 것은 실패가 아니다 — 원하던 상태가 이미 됐다. */
    @Test
    void 없는_주문을_취소해도_던지지_않는다() {
        PlacedOrder placed = broker.place(stop());
        broker.cancel(placed.id());

        broker.cancel(placed.id());

        assertThat(broker.restingOrders()).isEmpty();
    }

    @Test
    void 주문마다_다른_식별자를_준다() {
        assertThat(broker.place(stop()).id()).isNotEqualTo(broker.place(stop()).id());
    }

    private static OrderIntent entry(Direction direction) {
        return OrderIntent.entry(Symbol.BTC_USDT, direction,
                new OrderIntent.Sizing(Quantity.of("0.01"), Price.of("78000")));
    }

    private static OrderIntent stop() {
        return OrderIntent.protectAll(Symbol.BTC_USDT, Direction.LONG,
                new OrderIntent.Protection(
                        OrderKind.STOP_LOSS, Price.of("77000"), Price.of("78000")));
    }
}
