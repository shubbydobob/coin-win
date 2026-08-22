package com.coinwin.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.account.AccountFixtures;
import com.coinwin.account.adapter.out.memory.InMemoryExchangePositionAdapter;
import com.coinwin.account.domain.ExchangePosition;
import com.coinwin.account.domain.PositionMatch;
import com.coinwin.account.domain.PositionReconciliation;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.journal.JournalFixtures;
import com.coinwin.journal.application.port.in.QueryJournalUseCase;
import com.coinwin.journal.domain.ClosedTrade;
import com.coinwin.journal.domain.JournalSummary;
import com.coinwin.journal.domain.Trade;
import com.coinwin.journal.domain.TradeId;
import com.coinwin.journal.domain.TradeQuery;
import com.coinwin.market.domain.Symbol;
import com.coinwin.position.domain.Direction;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 서비스의 조율 규칙.
 *
 * <p><b>키도 DB 도 없이 전부 돈다</b> — 인메모리 어댑터와 손으로 만든 기록 스텁만 쓴다.
 * 그것이 포트를 둔 이유이고 명세 § 9 의 완료 조건 4번이다.
 */
class PositionReconciliationServiceTest {

    private static final Instant OBSERVED = Instant.parse("2026-08-23T01:53:00Z");

    @Test
    void 기록의_미청산_거래와_거래소_포지션을_맞춰_본다() {
        PositionReconciliation result = reconcile(
                List.of(JournalFixtures.open()),
                AccountFixtures.longPosition("0.1", OBSERVED));

        assertThat(result.isConsistent()).isTrue();
        assertThat(result.matches()).singleElement().isInstanceOf(PositionMatch.Agreed.class);
    }

    /**
     * <b>세워 둔 계획은 대조에 넣지 않는다.</b> {@code PlannedTrade} 는 아직 체결되지 않았으므로
     * 거래소에 있을 리가 없다. 넣으면 모든 계획이 "거래소에 없다" 는 불일치가 되고,
     * <b>경고가 언제나 켜져 있으면 아무도 보지 않는다.</b>
     */
    @Test
    void 아직_체결되지_않은_계획은_대조에_넣지_않는다() {
        PositionReconciliation result = reconcile(
                List.of(JournalFixtures.planned()), noPositions());

        assertThat(result.isConsistent()).isTrue();
        assertThat(result.matches()).isEmpty();
    }

    @Test
    void 계획과_포지션이_섞여_있으면_포지션만_고른다() {
        PositionReconciliation result = reconcile(
                List.of(JournalFixtures.planned(), JournalFixtures.open()),
                AccountFixtures.longPosition("0.1", OBSERVED));

        assertThat(result.matches()).singleElement().isInstanceOf(PositionMatch.Agreed.class);
    }

    /** 우리가 보는 종목이 아닌 포지션은 짝지어지지 않는다. 거래소는 계좌 전체를 돌려준다. */
    @Test
    void 다른_종목의_포지션은_섞이지_않는다() {
        ExchangePosition other = new ExchangePosition(
                new Symbol("ETHUSDT"), Direction.LONG, Quantity.of("1"),
                Price.of("3000"), Optional.of(Price.of("2500")), Money.of("0"), OBSERVED);

        PositionReconciliation result = reconcile(List.of(JournalFixtures.open()), other);

        assertThat(result.discrepancies()).singleElement()
                .isInstanceOf(PositionMatch.RecordedOnly.class);
    }

    /**
     * 관측 시각은 거래소가 말한 것을 쓴다. 여기서 지금을 부르면 응답을 기다린 시간만큼
     * 미래가 되고, 화면은 그것을 "이 순간의 사실" 로 읽는다.
     */
    @Test
    void 관측_시각은_거래소가_말한_것을_쓴다() {
        PositionReconciliation result = reconcile(
                List.of(), AccountFixtures.longPosition("0.1", OBSERVED));

        assertThat(result.observedAt()).isEqualTo(OBSERVED);
    }

    /** 포지션이 없으면 거래소가 시각을 말해 주지 않는다. 그때만 지금을 쓴다. */
    @Test
    void 포지션이_없으면_지금을_관측_시각으로_쓴다() {
        Instant before = Instant.now();

        PositionReconciliation result = reconcile(List.of(), noPositions());

        assertThat(result.observedAt()).isAfterOrEqualTo(before);
    }

    private static PositionReconciliation reconcile(
            List<Trade> activeTrades, ExchangePosition... positions) {
        return new PositionReconciliationService(
                new StubJournal(activeTrades),
                Optional.of(new InMemoryExchangePositionAdapter(positions))).reconcile();
    }

    private static ExchangePosition[] noPositions() {
        return new ExchangePosition[0];
    }

    /** 미청산 목록만 답하는 기록. 나머지 질문은 이 대조와 무관하다. */
    private record StubJournal(List<Trade> active) implements QueryJournalUseCase {

        @Override
        public Trade trade(TradeId id) {
            throw new UnsupportedOperationException("이 테스트는 단건 조회를 쓰지 않는다");
        }

        @Override
        public List<ClosedTrade> closedTrades(TradeQuery query) {
            return List.of();
        }

        @Override
        public List<Trade> activeTrades() {
            return active;
        }

        @Override
        public JournalSummary summarize(TradeQuery query) {
            return JournalSummary.of(List.of());
        }
    }
}
