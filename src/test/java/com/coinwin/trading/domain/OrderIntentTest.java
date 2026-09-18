package com.coinwin.trading.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.domain.Symbol;
import com.coinwin.position.domain.Direction;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 주문 의도가 성립하는 조건. */
class OrderIntentTest {

    @Test
    void 진입은_수량을_말해야_한다() {
        assertThatThrownBy(() -> new OrderIntent(Symbol.BTC_USDT, Direction.LONG,
                OrderKind.ENTRY, Optional.empty(), Optional.empty(), Price.of("78000")))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("수량을 말해야");
    }

    /** 전량 청산은 수량이 어긋나도 남는 포지션이 없다. 그것이 비워 두는 이유다. */
    @Test
    void 닫는_주문은_수량을_비워_전량을_뜻한다() {
        OrderIntent exit = new OrderIntent(Symbol.BTC_USDT, Direction.SHORT, OrderKind.EXIT,
                Optional.empty(), Optional.empty(), Price.of("78000"));

        assertThat(exit.closesEntirePosition()).isTrue();
    }

    @Test
    void 트리거_주문에_트리거가_없으면_성립하지_않는다() {
        assertThatThrownBy(() -> new OrderIntent(Symbol.BTC_USDT, Direction.LONG,
                OrderKind.STOP_LOSS, Optional.empty(), Optional.empty(), Price.of("78000")))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("트리거 가격이 맞지 않는다");
    }

    @Test
    void 시장가_주문에_트리거가_있으면_성립하지_않는다() {
        assertThatThrownBy(() -> new OrderIntent(Symbol.BTC_USDT, Direction.LONG,
                OrderKind.ENTRY, Optional.of(Quantity.of("0.01")),
                Optional.of(Price.of("77000")), Price.of("78000")))
                .isInstanceOf(InvalidOrderException.class);
    }

    @Test
    void 수량이_0_이면_주문이_아니다() {
        assertThatThrownBy(() -> OrderIntent.entry(Symbol.BTC_USDT, Direction.LONG,
                new OrderIntent.Sizing(Quantity.of("0"), Price.of("78000"))))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("전량은 비워 둔다");
    }

    /** 0.01 × 78,000 = 780. <b>수량이 아니라 이것이 위험의 크기다.</b> */
    @Test
    void 명목은_수량_곱하기_기준가다() {
        assertThat(OrderIntent.entry(Symbol.BTC_USDT, Direction.LONG,
                new OrderIntent.Sizing(Quantity.of("0.01"), Price.of("78000"))).notional())
                .isEqualTo(Money.of("780.00"));
    }

    /** 줄이는 주문은 위험을 만들지 않으므로 한계에 세지 않는다. */
    @Test
    void 닫는_주문의_명목은_0_이다() {
        OrderIntent stop = OrderIntent.protectAll(Symbol.BTC_USDT, Direction.SHORT,
                new OrderIntent.Protection(
                        OrderKind.STOP_LOSS, Price.of("79500"), Price.of("78000")));

        assertThat(stop.notional()).isEqualTo(Money.of("0"));
    }

    /** 롱을 닫는 매도와 숏을 여는 매도가 같은 값이 되면 안 된다. */
    @Test
    void 방향은_매수매도가_아니라_포지션의_방향이다() {
        assertThat(OrderIntent.protectAll(Symbol.BTC_USDT, Direction.SHORT,
                new OrderIntent.Protection(
                        OrderKind.STOP_LOSS, Price.of("79500"), Price.of("78000")))
                .position()).isEqualTo(Direction.SHORT);
    }
}
