package com.coinwin.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
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
                Price.of("59500"), MARK, liquidation(), Money.of("0"), AT))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new ExchangePosition(Symbol.BTC_USDT, null, Quantity.of("0.1"),
                Price.of("59500"), MARK, liquidation(), Money.of("0"), AT))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new ExchangePosition(Symbol.BTC_USDT, Direction.LONG,
                Quantity.of("0.1"), Price.of("59500"), MARK, liquidation(), Money.of("0"), null))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new ExchangePosition(Symbol.BTC_USDT, Direction.LONG,
                Quantity.of("0.1"), null, MARK, liquidation(), Money.of("0"), AT))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new ExchangePosition(Symbol.BTC_USDT, Direction.LONG,
                Quantity.of("0.1"), Price.of("59500"), MARK, null, Money.of("0"), AT))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new ExchangePosition(Symbol.BTC_USDT, Direction.LONG,
                Quantity.of("0.1"), Price.of("59500"), MARK, liquidation(), null, AT))
                .isInstanceOf(InvalidValueException.class);
    }

    /** 미실현 손익은 음수일 수 있다. 지고 있는 포지션도 포지션이다. */
    @Test
    void 미실현_손익은_음수일_수_있다() {
        ExchangePosition losing = new ExchangePosition(Symbol.BTC_USDT, Direction.SHORT,
                Quantity.of("0.1"), Price.of("59500"), MARK, liquidation(), Money.of("-8.10"), AT);

        assertThat(losing.unrealizedPnl()).isEqualTo(Money.of("-8.10"));
    }

    private static ExchangePosition position(Quantity quantity, Optional<Price> liquidation) {
        return new ExchangePosition(Symbol.BTC_USDT, Direction.LONG, quantity,
                Price.of("59500"), MARK, liquidation, Money.of("12.40"), AT);
    }

    /**
     * <b>수량이 아니라 명목이 위험의 크기다.</b> 이 프로젝트를 만든 손실이 정확히 이 수를
     * 몰라서 났다 — 0.13 BTC 라는 수는 얼마를 걸었는지를 말해 주지 않는다.
     */
    @Test
    void 명목은_표시가로_잰다() {
        ExchangePosition position = position(Quantity.of("0.13"), liquidation());

        // 평단 59500 이 아니라 표시가 60000 이다. 지금 닫으면 오가는 돈이 그쪽이다.
        assertThat(position.notional()).isEqualTo(Money.of("7800.00"));
    }

    /**
     * 청산까지의 거리는 <b>마지막 체결가가 아니라 표시가</b>에서 잰다. 청산이 트리거되는 값이
     * 그것이기 때문이다 — 체결가로 재면 그럴듯하지만 틀린 수가 나온다.
     */
    @Test
    void 청산까지의_거리는_표시가에서_잰다() {
        ExchangePosition position = position(Quantity.of("0.1"), Optional.of(Price.of("53765.06")));

        // (60000 − 53765.06) / 60000 = 10.3916%
        assertThat(position.liquidationDistance()).contains(Percentage.of("10.3916"));
    }

    /** 롱은 아래, 숏은 위. <b>거리에 부호를 붙이지 않는다</b> — 방향은 이미 따로 있다. */
    @Test
    void 숏의_청산가는_위에_있고_거리는_그래도_양수다() {
        ExchangePosition 숏 = new ExchangePosition(Symbol.BTC_USDT, Direction.SHORT,
                Quantity.of("0.1"), Price.of("59500"), MARK,
                Optional.of(Price.of("66043.21")), Money.of("-8.10"), AT);

        // (66043.21 − 60000) / 60000 = 10.0720%
        assertThat(숏.liquidationDistance()).contains(Percentage.of("10.0720"));
    }

    /** 거래소가 청산 지점을 말할 수 없으면 거리도 없다. <b>0% 는 "임박" 이라는 뜻이 된다.</b> */
    @Test
    void 청산가가_없으면_거리도_없다() {
        ExchangePosition position = position(Quantity.of("0.1"), Optional.empty());

        assertThat(position.liquidationDistance()).isEmpty();
    }

    /** 표시가. 평단 59500 에서 조금 오른 자리라 롱이 이기고 있는 상태다. */
    private static final Price MARK = Price.of("60000");

    private static Optional<Price> liquidation() {
        return Optional.of(Price.of("53765.06"));
    }
}
