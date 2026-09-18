package com.coinwin.trading.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.domain.Symbol;
import com.coinwin.position.domain.Direction;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 낸 주문이 성립하는 조건. */
class PlacedOrderTest {

    private static final Instant AT = Instant.parse("2026-09-18T00:00:00Z");
    private static final OrderId ID = new OrderId("paper-1");

    /**
     * 시장가는 체결되고 트리거는 걸려만 있다. <b>이 규칙이 타입에 있어야</b> 장부 브로커와
     * 거래소 어댑터가 같은 약속을 지키고, 두 기록을 나란히 놓을 수 있다.
     */
    @Test
    void 시장가_주문에_체결가가_없으면_성립하지_않는다() {
        assertThatThrownBy(() -> new PlacedOrder(
                ID, entry(), Optional.empty(), AT, TradingMode.PAPER))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("체결가가 맞지 않는다");
    }

    /** 걸려만 있는 주문에 체결가를 채우면 그 수가 손익 계산에 그대로 들어간다. */
    @Test
    void 트리거_주문에_체결가가_있으면_성립하지_않는다() {
        assertThatThrownBy(() -> new PlacedOrder(
                ID, stop(), Optional.of(Price.of("77000")), AT, TradingMode.PAPER))
                .isInstanceOf(InvalidOrderException.class);
    }

    @Test
    void 주문_식별자는_비어_있을_수_없다() {
        assertThatThrownBy(() -> new OrderId("  "))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("비어 있다");
    }

    @Test
    void 주문_식별자는_null_일_수_없다() {
        assertThatThrownBy(() -> new OrderId(null)).isInstanceOf(InvalidOrderException.class);
    }

    /**
     * 거부에는 이유가 있어야 한다. 없으면 사이클 기록에서 <b>아무것도 안 한 것과 막힌 것이
     * 같은 모양</b>이 된다.
     */
    @Test
    void 거부에는_이유가_있어야_한다() {
        assertThatThrownBy(() -> new RiskVerdict.Rejected(entry(), ""))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("이유가 있어야");
    }

    @Test
    void 이유가_null_인_거부는_성립하지_않는다() {
        assertThatThrownBy(() -> new RiskVerdict.Rejected(entry(), null))
                .isInstanceOf(InvalidOrderException.class);
    }

    private static OrderIntent entry() {
        return OrderIntent.entry(Symbol.BTC_USDT, Direction.LONG,
                new OrderIntent.Sizing(Quantity.of("0.01"), Price.of("78000")));
    }

    private static OrderIntent stop() {
        return OrderIntent.protectAll(Symbol.BTC_USDT, Direction.LONG,
                new OrderIntent.Protection(
                        OrderKind.STOP_LOSS, Price.of("77000"), Price.of("78000")));
    }
}
