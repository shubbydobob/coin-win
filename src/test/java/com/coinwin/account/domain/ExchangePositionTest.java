package com.coinwin.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.domain.Symbol;
import com.coinwin.position.domain.Direction;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 거래소 포지션이 성립하는 조건. */
class ExchangePositionTest {

    private static final Instant AT = Instant.parse("2026-08-23T01:53:00Z");

    /**
     * <b>수량 0 은 포지션이 아니다.</b> 거래소는 닫힌 종목도 {@code positionAmt: "0"} 으로
     * 돌려준다. 그것을 담으면 "포지션 있음" 이 되어 대조가 통째로 뒤집힌다.
     */
    @Test
    void 수량이_0_이면_포지션이_아니다() {
        assertThatThrownBy(() -> position(Quantity.of("0"), liquidation()))
                .isInstanceOf(InvalidAccountDataException.class)
                .hasMessageContaining("0 보다 커야");
    }

    /** 방향은 {@link Direction} 이 들고 있으므로 수량에 부호가 있으면 두 표현이 어긋난다. */
    @Test
    void 수량은_음수일_수_없다() {
        assertThatThrownBy(() -> position(Quantity.of("-0.1"), liquidation()))
                .isInstanceOf(InvalidValueException.class);
    }

    /**
     * 거래소가 청산 지점을 말할 수 없으면 <b>비어 있다.</b> 0 원짜리 청산가로 담으면 화면이
     * "곧 청산된다" 는 뜻으로 읽는다 — 손익비를 {@code Optional} 로 둔 것과 같은 규칙이다.
     */
    @Test
    void 청산가는_비어_있을_수_있다() {
        ExchangePosition position = position(Quantity.of("0.1"), Optional.empty());

        assertThat(position.liquidationPrice()).isEmpty();
    }

    @Test
    void 청산가가_있다면_0_보다_커야_한다() {
        assertThatThrownBy(() -> position(Quantity.of("0.1"), Optional.of(Price.of("0"))))
                .isInstanceOf(InvalidAccountDataException.class)
                .hasMessageContaining("없는 것과 0 은 다른 사실");
    }

    @Test
    void 필수값이_없으면_만들_수_없다() {
        assertThatThrownBy(() -> new ExchangePosition(null, Direction.LONG, Quantity.of("0.1"),
                Price.of("59500"), liquidation(), Money.of("0"), AT))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new ExchangePosition(Symbol.BTC_USDT, null, Quantity.of("0.1"),
                Price.of("59500"), liquidation(), Money.of("0"), AT))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new ExchangePosition(Symbol.BTC_USDT, Direction.LONG,
                Quantity.of("0.1"), Price.of("59500"), liquidation(), Money.of("0"), null))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new ExchangePosition(Symbol.BTC_USDT, Direction.LONG,
                Quantity.of("0.1"), null, liquidation(), Money.of("0"), AT))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new ExchangePosition(Symbol.BTC_USDT, Direction.LONG,
                Quantity.of("0.1"), Price.of("59500"), null, Money.of("0"), AT))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new ExchangePosition(Symbol.BTC_USDT, Direction.LONG,
                Quantity.of("0.1"), Price.of("59500"), liquidation(), null, AT))
                .isInstanceOf(InvalidValueException.class);
    }

    /** 미실현 손익은 음수일 수 있다. 지고 있는 포지션도 포지션이다. */
    @Test
    void 미실현_손익은_음수일_수_있다() {
        ExchangePosition losing = new ExchangePosition(Symbol.BTC_USDT, Direction.SHORT,
                Quantity.of("0.1"), Price.of("59500"), liquidation(), Money.of("-8.10"), AT);

        assertThat(losing.unrealizedPnl()).isEqualTo(Money.of("-8.10"));
    }

    private static ExchangePosition position(Quantity quantity, Optional<Price> liquidation) {
        return new ExchangePosition(Symbol.BTC_USDT, Direction.LONG, quantity,
                Price.of("59500"), liquidation, Money.of("12.40"), AT);
    }

    private static Optional<Price> liquidation() {
        return Optional.of(Price.of("53765.06"));
    }
}
