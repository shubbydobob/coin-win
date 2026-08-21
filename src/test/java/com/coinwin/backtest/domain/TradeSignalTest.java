package com.coinwin.backtest.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Price;
import com.coinwin.indicator.domain.BandPosition;
import com.coinwin.journal.domain.MarketContext;
import com.coinwin.position.domain.Direction;
import com.coinwin.position.domain.EntryLadder;
import com.coinwin.position.domain.PlannedEntry;
import com.coinwin.position.domain.PositionPlan;
import org.junit.jupiter.api.Test;

class TradeSignalTest {

    private static final PositionPlan LONG_PLAN = new PositionPlan(
            Direction.LONG,
            EntryLadder.of(PlannedEntry.of("59200", "50"), PlannedEntry.of("59000", "50")),
            Price.of("58900"), Price.of("62000"), 10);

    private static final MarketContext CONTEXT = new MarketContext(
            Price.of("60000"), BandPosition.INSIDE, BandPosition.INSIDE, "지지대 반전");

    @Test
    void 방향이_일치하면_만들어진다() {
        assertThatCode(() -> new TradeSignal(Direction.LONG, LONG_PLAN, CONTEXT))
                .doesNotThrowAnyException();
    }

    /**
     * 방향은 계획에도 있고 신호에도 있다. 두 곳에 적힌 같은 사실은 갈라지므로 생성 시점에
     * 막는다. 갈라지면 집계는 숏으로 세고 손익은 롱 공식으로 계산하는 일이 생긴다.
     */
    @Test
    void 신호와_계획의_방향이_다르면_거부된다() {
        assertThatThrownBy(() -> new TradeSignal(Direction.SHORT, LONG_PLAN, CONTEXT))
                .isInstanceOf(InvalidBacktestException.class)
                .hasMessageContaining("방향");
    }

    @Test
    void null은_거부된다() {
        assertThatThrownBy(() -> new TradeSignal(null, LONG_PLAN, CONTEXT))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new TradeSignal(Direction.LONG, null, CONTEXT))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new TradeSignal(Direction.LONG, LONG_PLAN, null))
                .isInstanceOf(InvalidValueException.class);
    }
}
