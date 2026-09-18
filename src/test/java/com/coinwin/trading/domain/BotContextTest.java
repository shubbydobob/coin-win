package com.coinwin.trading.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.domain.Symbol;
import com.coinwin.position.domain.Direction;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 한 사이클이 보는 세상이 성립하는 조건. */
class BotContextTest {

    private static final Instant AT = Instant.parse("2026-09-18T03:00:00Z");
    private static final Money EQUITY = Money.of("800");

    /**
     * 열린 포지션이 있다고 했으면 수도 0 이 아니어야 한다. 어긋나면 동시 포지션 한계가
     * 통과하고 <b>이미 열린 자리에 하나를 더 연다.</b>
     */
    @Test
    void 포지션이_있는데_계좌가_0_개라고_하면_거부한다() {
        assertThatThrownBy(() -> new BotContext(
                view(), AccountState.flat(EQUITY), Optional.of(position()), List.of()))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("두 값이 어긋났다");
    }

    @Test
    void 포지션과_수가_맞으면_성립한다() {
        BotContext context = new BotContext(
                view(), new AccountState(EQUITY, 1, Money.of("0"), Money.of("0")),
                Optional.of(position()), List.of());

        assertThat(context.open()).contains(position());
    }

    @Test
    void 포지션이_없으면_수가_0_이어도_된다() {
        assertThat(new BotContext(
                view(), AccountState.flat(EQUITY), Optional.empty(), List.of()).open())
                .isEmpty();
    }

    @Test
    void 열린_포지션의_수량은_0_보다_커야_한다() {
        assertThatThrownBy(() ->
                new BotPosition(Direction.LONG, Quantity.of("0"), Price.of("78000")))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("0 보다 커야");
    }

    /** 규칙이 없을 때 봇이 할 일은 추측하는 것이 아니라 가만히 있는 것이다. */
    @Test
    void 기본_전략은_아무것도_하지_않는다() {
        HoldStrategy hold = new HoldStrategy();

        assertThat(hold.decide(new BotContext(
                view(), new AccountState(EQUITY, 1, Money.of("0"), Money.of("0")),
                Optional.of(position()), List.of())).isEmpty()).isTrue();
        assertThat(hold.name()).isNotBlank();
    }

    private static MarketView view() {
        return new MarketView(Symbol.BTC_USDT, Price.of("78000"), List.of(), AT);
    }

    private static BotPosition position() {
        return new BotPosition(Direction.SHORT, Quantity.of("0.1"), Price.of("78000"));
    }
}
