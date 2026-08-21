package com.coinwin.journal.application.service;

import static com.coinwin.journal.JournalFixtures.EXIT_AT;
import static com.coinwin.journal.JournalFixtures.PLANNED_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import com.coinwin.journal.JournalFixtures;
import com.coinwin.journal.adapter.out.memory.InMemoryTradeAdapter;
import com.coinwin.journal.domain.ClosedTrade;
import com.coinwin.journal.domain.Exit;
import com.coinwin.journal.domain.ExitReason;
import com.coinwin.journal.domain.InvalidTradeException;
import com.coinwin.journal.domain.JournalSummary;
import com.coinwin.journal.domain.OpenTrade;
import com.coinwin.journal.domain.PlannedTrade;
import com.coinwin.journal.domain.TradeClosure;
import com.coinwin.journal.domain.TradeCosts;
import com.coinwin.journal.domain.TradeId;
import com.coinwin.journal.domain.TradeNotFoundException;
import com.coinwin.journal.domain.TradeQuery;
import com.coinwin.position.domain.Direction;
import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 애플리케이션 서비스. <b>Spring 컨텍스트도 DB 도 띄우지 않는다.</b>
 *
 * <p>Phase 5 완료 조건이 이것을 직접 요구한다 — "애플리케이션 서비스 테스트가 DB 없이
 * 인메모리 어댑터만으로 전부 통과". 그 대체가 정당한 근거는 두 어댑터가 같은 계약 스위트를
 * 통과한다는 사실이다({@code TradeRepositoryContract}).
 */
class TradeJournalServiceTest {

    private InMemoryTradeAdapter trades;
    private TradeJournalService service;

    @BeforeEach
    void 인메모리_어댑터로_서비스를_조립한다() {
        trades = new InMemoryTradeAdapter();
        service = new TradeJournalService(trades, trades, Clock.fixed(PLANNED_AT, ZoneOffset.UTC));
    }

    /**
     * Phase 5 완료 조건. 계획 → 체결 → 청산이 한 건으로 이어지고 집계에 반영된다.
     */
    @Test
    void 실제_매매_한_건이_처음부터_끝까지_기록되고_집계에_반영된다() {
        PlannedTrade planned = service.planTrade(JournalFixtures.longPlan());

        service.recordFills(planned.id(),
                JournalFixtures.bothLegsFilled(), JournalFixtures.context());
        ClosedTrade closed = service.closeTrade(planned.id(), new TradeClosure(
                new Exit(Price.of("64000"), EXIT_AT),
                ExitReason.PLANNED_TARGET,
                TradeCosts.of("5.00", "1.20")));

        assertThat(closed.realizedPnl()).isEqualTo(Money.of("443.80"));

        JournalSummary summary = service.summarize(TradeQuery.all());
        assertThat(summary.totalTrades()).isEqualTo(1);
        assertThat(summary.followed().trades()).isEqualTo(1);
        assertThat(summary.followed().realizedPnl()).isEqualTo(Money.of("443.80"));
        assertThat(summary.planAdherence()).isEqualTo(Percentage.of("100"));
        assertThat(service.activeTrades()).isEmpty();
    }

    @Test
    void 계획_시각은_시계가_찍는다() {
        assertThat(service.planTrade(JournalFixtures.longPlan()).plannedAt())
                .isEqualTo(PLANNED_AT);
    }

    @Test
    void 계획을_세우면_활성_목록에_들어간다() {
        PlannedTrade planned = service.planTrade(JournalFixtures.longPlan());

        assertThat(service.activeTrades()).containsExactly(planned);
        assertThat(service.trade(planned.id())).isEqualTo(planned);
    }

    @Test
    void 체결_기록은_계획_상태의_거래에만_할_수_있다() {
        PlannedTrade planned = service.planTrade(JournalFixtures.longPlan());
        TradeId id = planned.id();
        service.recordFills(id, JournalFixtures.bothLegsFilled(), JournalFixtures.context());

        assertThatThrownBy(() -> service.recordFills(
                id, JournalFixtures.bothLegsFilled(), JournalFixtures.context()))
                .isInstanceOf(InvalidTradeException.class)
                .hasMessageContaining("계획 상태의 거래에만 할 수 있다")
                .hasMessageContaining("지금은 체결됨 다");
    }

    @Test
    void 청산은_체결된_거래에만_할_수_있다() {
        PlannedTrade planned = service.planTrade(JournalFixtures.longPlan());
        TradeId id = planned.id();

        assertThatThrownBy(() -> service.closeTrade(id, closure()))
                .isInstanceOf(InvalidTradeException.class)
                .hasMessageContaining("체결됨 상태의 거래에만 할 수 있다")
                .hasMessageContaining("지금은 계획 다");
    }

    @Test
    void 이미_청산된_거래는_다시_청산할_수_없다() {
        PlannedTrade planned = service.planTrade(JournalFixtures.longPlan());
        TradeId id = planned.id();
        service.recordFills(id, JournalFixtures.bothLegsFilled(), JournalFixtures.context());
        service.closeTrade(id, closure());

        assertThatThrownBy(() -> service.closeTrade(id, closure()))
                .isInstanceOf(InvalidTradeException.class)
                .hasMessageContaining("지금은 청산됨 다");
    }

    @Test
    void 없는_거래를_지목하면_찾을_수_없다고_알린다() {
        TradeId missing = TradeId.random();

        assertThatThrownBy(() -> service.trade(missing))
                .isInstanceOf(TradeNotFoundException.class)
                .hasMessageContaining("그런 거래가 없다");
        assertThatThrownBy(() -> service.closeTrade(missing, closure()))
                .isInstanceOf(TradeNotFoundException.class);
    }

    /** 체결 기록은 열린 거래로 남고 아직 집계에 들어가지 않는다. */
    @Test
    void 체결만_된_거래는_집계에_들어가지_않는다() {
        PlannedTrade planned = service.planTrade(JournalFixtures.longPlan());
        OpenTrade open = service.recordFills(planned.id(),
                JournalFixtures.bothLegsFilled(), JournalFixtures.context());

        assertThat(service.activeTrades()).containsExactly(open);
        assertThat(service.closedTrades(TradeQuery.all())).isEmpty();
        assertThat(service.summarize(TradeQuery.all())).isEqualTo(JournalSummary.empty());
    }

    /** 조회 조건은 목록과 집계에 똑같이 걸린다. 화면마다 필터를 다시 쓰지 않게 하려는 것이다. */
    @Test
    void 조회_조건이_집계에도_그대로_걸린다() {
        trades.save(JournalFixtures.closedEndingAt(EXIT_AT, ExitReason.PLANNED_TARGET));
        trades.save(JournalFixtures.shortClosedEndingAt(
                EXIT_AT.plusSeconds(86_400), ExitReason.HELD_PAST_STOP));

        JournalSummary onlyShort = service.summarize(
                TradeQuery.all().withDirection(Direction.SHORT));

        assertThat(service.closedTrades(TradeQuery.all())).hasSize(2);
        assertThat(onlyShort.totalTrades()).isEqualTo(1);
        assertThat(onlyShort.broken().trades()).isEqualTo(1);
        assertThat(onlyShort.followed().isEmpty()).isTrue();
    }

    private static TradeClosure closure() {
        return new TradeClosure(new Exit(Price.of("64000"), EXIT_AT),
                ExitReason.PLANNED_TARGET, TradeCosts.none());
    }
}
