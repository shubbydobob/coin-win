package com.coinwin.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.account.AccountFixtures;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.domain.Symbol;
import com.coinwin.position.domain.Direction;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 포지션을 닫는 미체결 주문이 성립하는 조건. */
class ProtectiveOrderTest {

    /**
     * <b>추격 손절도 손절이다.</b> 트리거가 미리 정해져 있지 않을 뿐 하는 일은 같다.
     * 세지 않으면 추격만 걸어 둔 사람에게 거짓 경고가 뜨고, 늘 떠 있는 경고는 아무것도
     * 경고하지 않는다.
     */
    @Test
    void 추격_손절도_손절로_센다() {
        assertThat(AccountFixtures.trailingStop(Direction.LONG).stopsLoss()).isTrue();
    }

    @Test
    void 익절은_손절이_아니다() {
        assertThat(AccountFixtures.takeProfit(Direction.LONG, "64000").stopsLoss()).isFalse();
    }

    /** 트리거 없는 손절은 어디서 발동하는지 말할 수 없다. 주문이 아니다. */
    @Test
    void 추격이_아닌데_트리거가_없으면_주문이_아니다() {
        assertThatThrownBy(() -> ProtectiveOrder.entirePosition(
                Symbol.BTC_USDT, Direction.LONG, ProtectiveOrderKind.STOP_LOSS, null))
                .isInstanceOf(InvalidAccountDataException.class)
                .hasMessageContaining("트리거 가격이 없는");
    }

    /**
     * <b>0 을 담아 "전량" 을 뜻하게 하지 않는다.</b> 그러면 전량 손절과 아무것도 닫지 않는
     * 주문이 같은 값이 된다 — 없는 것과 0 은 다른 사실이다.
     */
    @Test
    void 수량이_0_이면_주문이_아니다() {
        assertThatThrownBy(() -> new ProtectiveOrder(
                Symbol.BTC_USDT, Direction.LONG, ProtectiveOrderKind.STOP_LOSS,
                Optional.of(Price.of("58000")), Optional.of(Quantity.of("0"))))
                .isInstanceOf(InvalidAccountDataException.class)
                .hasMessageContaining("비워 둔다");
    }

    @Test
    void 수량이_비어_있으면_전량이다() {
        ProtectiveOrder order = AccountFixtures.stop(Direction.LONG, "58000");

        assertThat(order.closesEntirePosition()).isTrue();
        assertThat(order.coverageOf(Quantity.of("0.25"))).isEqualTo(Quantity.of("0.25"));
    }

    /** 주문 수량이 포지션보다 크면 남는 만큼은 닫을 것이 없다. 두 번 세면 안 된다. */
    @Test
    void 포지션보다_큰_주문은_포지션_수량까지만_덮는다() {
        ProtectiveOrder order = AccountFixtures.partialStop(Direction.LONG, "58000", "0.5");

        assertThat(order.coverageOf(Quantity.of("0.1"))).isEqualTo(Quantity.of("0.1"));
    }

    @Test
    void 포지션보다_작은_주문은_그만큼만_덮는다() {
        ProtectiveOrder order = AccountFixtures.partialStop(Direction.LONG, "58000", "0.04");

        assertThat(order.coverageOf(Quantity.of("0.1"))).isEqualTo(Quantity.of("0.04"));
    }
}
