package com.coinwin.journal.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import com.coinwin.journal.JournalFixtures;
import com.coinwin.journal.domain.ClosedTrade;
import com.coinwin.journal.domain.ExitReason;
import com.coinwin.journal.domain.OpenTrade;
import com.coinwin.journal.domain.PlannedTrade;
import com.coinwin.journal.domain.Trade;
import com.coinwin.journal.domain.TradeId;
import com.coinwin.journal.domain.TradeQuery;
import com.coinwin.position.domain.Direction;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 거래 저장소의 계약. <b>인메모리 어댑터와 JPA 어댑터가 모두 이 스위트를 통과해야 한다.</b>
 *
 * <p>어댑터마다 테스트를 따로 쓰면 각자 자기 구현이 하는 일을 검사하게 되고, 포트가 하나의
 * 약속인지는 아무도 확인하지 않는다. Phase 5 완료 조건이 "서비스 테스트가 DB 없이 인메모리
 * 어댑터만으로 전부 통과" 인데, <b>그 말이 성립하려면 두 어댑터가 같은 답을 내야 한다.</b>
 * 그 확인이 이 스위트다.
 *
 * <p>근거: {@code .claude/docs/testing.md} — "하나의 테스트 스위트를 모든 어댑터 구현체에
 * 대해 실행한다."
 */
public abstract class TradeRepositoryContract {

    protected static final Instant BASE = Instant.parse("2026-08-01T00:00:00Z");

    protected abstract SaveTradePort savePort();

    protected abstract LoadTradesPort loadPort();

    protected static Instant at(int hoursFromBase) {
        return BASE.plus(Duration.ofHours(hoursFromBase));
    }

    @Test
    void 저장한_계획을_식별자로_다시_읽는다() {
        PlannedTrade planned = JournalFixtures.plannedAt(BASE);

        savePort().save(planned);

        assertThat(loadPort().findById(planned.id())).contains(planned);
    }

    @Test
    void 없는_식별자는_빈_값을_돌려준다() {
        assertThat(loadPort().findById(TradeId.random())).isEmpty();
    }

    /** 체결과 청산은 새 행이 아니라 같은 거래의 다음 상태다. 식별자가 그것을 보장한다. */
    @Test
    void 같은_식별자로_다시_저장하면_상태가_바뀐다() {
        PlannedTrade planned = JournalFixtures.plannedAt(BASE);
        savePort().save(planned);

        OpenTrade open = planned.fill(JournalFixtures.bothLegsFilled(), JournalFixtures.context());
        savePort().save(open);

        assertThat(loadPort().findById(planned.id())).contains(open);
        assertThat(loadPort().findActive()).hasSize(1);
    }

    /**
     * 값이 온전히 왕복하는지. 이 스위트에서 <b>JPA 매핑이 실제로 검증되는 지점</b>이다.
     *
     * <p>스케일이 깎이거나 분할 진입의 둘째 체결이 사라져도 나머지 테스트는 전부 통과한다.
     * 파생값(평단·손익·반사실)까지 대조해야 원본이 온전한지 알 수 있다.
     */
    @Test
    void 닫힌_거래의_모든_값이_온전히_왕복한다() {
        ClosedTrade original = JournalFixtures.closedAtTarget();

        savePort().save(original);
        ClosedTrade loaded = (ClosedTrade) loadPort().findById(original.id()).orElseThrow();

        assertThat(loaded.entries().count()).isEqualTo(2);
        assertThat(loaded.averageEntryPrice()).isEqualTo(Price.of("59500.00"));
        assertThat(loaded.grossPnl()).isEqualTo(Money.of("450.00"));
        assertThat(loaded.realizedPnl()).isEqualTo(Money.of("443.80"));
        assertThat(loaded.lossIfStopHonored()).isEqualTo(Money.of("-155.00"));
        assertThat(loaded.context().rationale()).isEqualTo("4h 59,000 지지 3회 확인");
        assertThat(loaded.plan()).isEqualTo(original.plan());
        assertThat(loaded).isEqualTo(original);
    }

    @Test
    void 닫히지_않은_거래는_닫힌_거래_조회에_들지_않는다() {
        savePort().save(JournalFixtures.plannedAt(BASE));
        savePort().save(JournalFixtures.open());
        savePort().save(JournalFixtures.closedEndingAt(at(30), ExitReason.PLANNED_TARGET));

        assertThat(loadPort().findClosed(TradeQuery.all())).hasSize(1);
    }

    @Test
    void 닫힌_거래는_활성_목록에_들지_않는다() {
        savePort().save(JournalFixtures.plannedAt(BASE));
        savePort().save(JournalFixtures.closedEndingAt(at(30), ExitReason.PLANNED_TARGET));

        assertThat(loadPort().findActive())
                .hasSize(1)
                .allMatch(PlannedTrade.class::isInstance);
    }

    /** 순서는 어댑터의 사정이 아니라 계약이다. 거래 간격 집계가 여기에 기댄다. */
    @Test
    void 닫힌_거래는_진입_시각_오름차순으로_돌려준다() {
        savePort().save(JournalFixtures.closedEndingAt(at(60), ExitReason.PLANNED_TARGET));
        savePort().save(JournalFixtures.closedEndingAt(at(20), ExitReason.PLANNED_TARGET));
        savePort().save(JournalFixtures.closedEndingAt(at(40), ExitReason.PLANNED_TARGET));

        List<Instant> openedAt = loadPort().findClosed(TradeQuery.all()).stream()
                .map(ClosedTrade::openedAt).toList();

        assertThat(openedAt).isSorted().hasSize(3);
    }

    /** 구간은 반열림 {@code [from, to)} 다. 끝 시각의 거래가 딸려 오면 월별 집계에서 중복된다. */
    @Test
    void 청산_구간은_시작을_포함하고_끝을_제외한다() {
        savePort().save(JournalFixtures.closedEndingAt(at(20), ExitReason.PLANNED_TARGET));
        savePort().save(JournalFixtures.closedEndingAt(at(40), ExitReason.PLANNED_TARGET));
        savePort().save(JournalFixtures.closedEndingAt(at(60), ExitReason.PLANNED_TARGET));

        List<ClosedTrade> found = loadPort().findClosed(
                TradeQuery.all().closedBetween(at(20), at(60)));

        assertThat(found).extracting(ClosedTrade::closedAt).containsExactly(at(20), at(40));
    }

    @Test
    void 방향으로_거른다() {
        savePort().save(JournalFixtures.closedEndingAt(at(20), ExitReason.PLANNED_TARGET));
        savePort().save(JournalFixtures.shortClosedEndingAt(at(40), ExitReason.PLANNED_TARGET));

        assertThat(loadPort().findClosed(TradeQuery.all().withDirection(Direction.SHORT)))
                .singleElement()
                .satisfies(trade ->
                        assertThat(trade.plan().direction()).isEqualTo(Direction.SHORT));
    }

    @Test
    void 청산_이유로_거른다() {
        savePort().save(JournalFixtures.closedEndingAt(at(20), ExitReason.PLANNED_TARGET));
        savePort().save(JournalFixtures.closedEndingAt(at(40), ExitReason.HELD_PAST_STOP));
        savePort().save(JournalFixtures.closedEndingAt(at(60), ExitReason.LIQUIDATED));

        assertThat(loadPort().findClosed(
                TradeQuery.all().withExitReason(ExitReason.HELD_PAST_STOP)))
                .singleElement()
                .satisfies(trade ->
                        assertThat(trade.closedAt()).isEqualTo(at(40)));
    }

    /**
     * 계획 준수 여부로 거른다. 저장된 컬럼이 아니라 <b>청산 이유에서 파생되는</b> 조건이라,
     * 두 어댑터가 같은 답을 내는지가 특히 갈리기 쉬운 자리다.
     */
    @Test
    void 계획_준수_여부로_거른다() {
        savePort().save(JournalFixtures.closedEndingAt(at(20), ExitReason.PLANNED_TARGET));
        savePort().save(JournalFixtures.closedEndingAt(at(40), ExitReason.PLANNED_STOP));
        savePort().save(JournalFixtures.closedEndingAt(at(60), ExitReason.HELD_PAST_STOP));
        savePort().save(JournalFixtures.closedEndingAt(at(80), ExitReason.MANUAL_EARLY));

        assertThat(loadPort().findClosed(TradeQuery.all().withFollowedPlan(true))).hasSize(2);
        assertThat(loadPort().findClosed(TradeQuery.all().withFollowedPlan(false))).hasSize(2);
    }

    @Test
    void 여러_조건은_함께_걸린다() {
        savePort().save(JournalFixtures.closedEndingAt(at(20), ExitReason.HELD_PAST_STOP));
        savePort().save(JournalFixtures.shortClosedEndingAt(at(40), ExitReason.HELD_PAST_STOP));
        savePort().save(JournalFixtures.shortClosedEndingAt(at(60), ExitReason.PLANNED_TARGET));

        TradeQuery query = TradeQuery.all()
                .withDirection(Direction.SHORT)
                .withFollowedPlan(false);

        assertThat(loadPort().findClosed(query))
                .singleElement()
                .satisfies(trade -> assertThat(trade.closedAt()).isEqualTo(at(40)));
    }

    @Test
    void 조건에_드는_거래가_없으면_빈_목록이다() {
        savePort().save(JournalFixtures.closedEndingAt(at(20), ExitReason.PLANNED_TARGET));

        assertThat(loadPort().findClosed(TradeQuery.all().withDirection(Direction.SHORT)))
                .isEmpty();
    }

    @Test
    void 저장된_것이_없으면_모두_빈_목록이다() {
        assertThat(loadPort().findClosed(TradeQuery.all())).isEmpty();
        assertThat(loadPort().findActive()).isEmpty();
    }

    /** 같은 질의는 같은 답을 낸다. */
    @Test
    void 같은_질의를_두_번_해도_같은_결과다() {
        savePort().save(JournalFixtures.closedEndingAt(at(20), ExitReason.PLANNED_TARGET));
        savePort().save(JournalFixtures.closedEndingAt(at(40), ExitReason.PLANNED_STOP));

        List<ClosedTrade> first = loadPort().findClosed(TradeQuery.all());
        List<ClosedTrade> second = loadPort().findClosed(TradeQuery.all());

        assertThat(first).isEqualTo(second);
    }

    /** 열려 있는 거래도 값이 온전히 왕복해야 한다 — 진입 맥락은 이 상태에서만 기록된다. */
    @Test
    void 열린_거래의_진입_맥락이_왕복한다() {
        OpenTrade open = JournalFixtures.open();

        savePort().save(open);
        Trade loaded = loadPort().findById(open.id()).orElseThrow();

        assertThat(loaded).isEqualTo(open);
    }
}
