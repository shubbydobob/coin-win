package com.coinwin.journal.domain;

import static com.coinwin.journal.JournalFixtures.EXIT_AT;
import static com.coinwin.journal.JournalFixtures.FIRST_FILL_AT;
import static com.coinwin.journal.JournalFixtures.PLANNED_AT;
import static com.coinwin.journal.JournalFixtures.SECOND_FILL_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.journal.JournalFixtures;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** 계획 → 체결 → 청산의 전이와, 각 단계에서 거부해야 하는 것들. */
class TradeLifecycleTest {

    @Test
    void 계획은_체결되면_열린_거래가_되고_식별자를_유지한다() {
        PlannedTrade planned = JournalFixtures.planned();

        OpenTrade open = planned.fill(JournalFixtures.bothLegsFilled(), JournalFixtures.context());

        assertThat(open.id()).isEqualTo(planned.id());
        assertThat(open.plan()).isEqualTo(planned.plan());
        assertThat(open.plannedAt()).isEqualTo(PLANNED_AT);
    }

    /** 진입 시각은 별도 필드가 아니라 첫 체결에서 나온다. 두 곳에 적힌 같은 사실은 갈라진다. */
    @Test
    void 열린_거래의_진입_시각은_첫_체결_시각이다() {
        assertThat(JournalFixtures.open().openedAt()).isEqualTo(FIRST_FILL_AT);
    }

    @Test
    void 닫힌_거래의_청산_시각은_청산_체결_시각이다() {
        ClosedTrade closed = JournalFixtures.closedAtTarget();

        assertThat(closed.closedAt()).isEqualTo(EXIT_AT);
        assertThat(closed.openedAt()).isEqualTo(FIRST_FILL_AT);
        assertThat(closed.holdingPeriod()).isEqualTo(Duration.ofHours(8));
    }

    @Test
    void 체결은_계획보다_앞설_수_없다() {
        PlannedTrade planned = JournalFixtures.planned();
        ExecutedEntries tooEarly = ExecutedEntries.of(
                new Fill(Price.of("60000"), Quantity.of("0.05"), PLANNED_AT.minusSeconds(1)));

        assertThatThrownBy(() -> planned.fill(tooEarly, JournalFixtures.context()))
                .isInstanceOf(InvalidTradeException.class)
                .hasMessageContaining("체결은 계획보다 앞설 수 없다");
    }

    @Test
    void 청산은_마지막_진입_체결보다_앞설_수_없다() {
        OpenTrade open = JournalFixtures.open();
        TradeClosure tooEarly = new TradeClosure(
                new Exit(Price.of("64000"), SECOND_FILL_AT.minusSeconds(1)),
                ExitReason.PLANNED_TARGET,
                TradeCosts.none());

        assertThatThrownBy(() -> open.close(tooEarly))
                .isInstanceOf(InvalidTradeException.class)
                .hasMessageContaining("청산은 마지막 진입 체결보다 앞설 수 없다");
    }

    /** 같은 시각의 청산은 허용한다. 시장가 청산은 마지막 체결과 같은 초에 들어올 수 있다. */
    @Test
    void 마지막_체결과_같은_시각의_청산은_허용한다() {
        OpenTrade open = JournalFixtures.open();

        ClosedTrade closed = open.close(new TradeClosure(
                new Exit(Price.of("64000"), SECOND_FILL_AT),
                ExitReason.PLANNED_TARGET,
                TradeCosts.none()));

        assertThat(closed.holdingPeriod()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void 계획한_분할이_모두_체결됐는지를_구분한다() {
        assertThat(JournalFixtures.open().fullyFilled()).isTrue();

        OpenTrade partial = JournalFixtures.planned()
                .fill(JournalFixtures.firstLegOnly(), JournalFixtures.context());

        assertThat(partial.fullyFilled()).isFalse();
    }

    /** 1차만 체결된 포지션의 평단은 계획 평단이 아니라 그 한 건의 가격이다. */
    @Test
    void 부분_체결의_평단은_체결된_것만으로_결정된다() {
        OpenTrade partial = JournalFixtures.planned()
                .fill(JournalFixtures.firstLegOnly(), JournalFixtures.context());

        assertThat(partial.averageEntryPrice()).isEqualTo(Price.of("60000.00"));
        assertThat(partial.quantity()).isEqualTo(Quantity.of("0.05"));
    }

    @Test
    void 계획_체결_청산_모두_같은_계획을_들고_있다() {
        ClosedTrade closed = JournalFixtures.closedAtTarget();

        assertThat(closed.plan()).isEqualTo(JournalFixtures.longPlan());
        assertThat(closed.context().rationale()).isEqualTo("4h 59,000 지지 3회 확인");
    }
}
