package com.coinwin.journal.application.service;

import com.coinwin.journal.application.TradeClosedEvent;
import com.coinwin.journal.application.port.in.QueryJournalUseCase;
import com.coinwin.journal.application.port.in.RecordTradeUseCase;
import com.coinwin.journal.application.port.out.LoadTradesPort;
import com.coinwin.journal.application.port.out.SaveTradePort;
import com.coinwin.journal.domain.ClosedTrade;
import com.coinwin.journal.domain.ExecutedEntries;
import com.coinwin.journal.domain.InvalidTradeException;
import com.coinwin.journal.domain.JournalSummary;
import com.coinwin.journal.domain.MarketContext;
import com.coinwin.journal.domain.OpenTrade;
import com.coinwin.journal.domain.PlannedTrade;
import com.coinwin.journal.domain.Trade;
import com.coinwin.journal.domain.TradeClosure;
import com.coinwin.journal.domain.TradeId;
import com.coinwin.journal.domain.TradeNotFoundException;
import com.coinwin.journal.domain.TradeQuery;
import com.coinwin.position.domain.PositionPlan;
import java.time.Clock;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * 기록과 조회를 잇는 조율자. 계산은 하지 않는다.
 *
 * <p>조회 → 도메인 호출 → 저장. 상태 전이 규칙은 {@code Trade} 가 갖고 있고, 여기서 하는
 * 일은 <b>지금 상태가 그 전이를 받을 수 있는 상태인지</b> 확인하는 것뿐이다. 그 확인마저
 * 도메인에 두지 않는 이유는 "저장소에서 읽은 것이 어느 상태인가" 가 조율의 문제이기 때문이다.
 *
 * <p>시각은 {@link Clock} 이 찍는다. {@code Instant.now()} 를 직접 부르면 계획 시각을
 * 고정할 수 없어 테스트가 시각에 대한 단언을 포기하게 된다.
 */
@Service
public class TradeJournalService implements RecordTradeUseCase, QueryJournalUseCase {

    private final SaveTradePort saveTrade;
    private final LoadTradesPort loadTrades;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public TradeJournalService(SaveTradePort saveTrade, LoadTradesPort loadTrades, Clock clock,
            ApplicationEventPublisher events) {
        this.saveTrade = saveTrade;
        this.loadTrades = loadTrades;
        this.clock = clock;
        this.events = events;
    }

    @Override
    public PlannedTrade planTrade(PositionPlan plan) {
        PlannedTrade planned = PlannedTrade.of(plan, clock.instant());
        saveTrade.save(planned);
        return planned;
    }

    @Override
    public OpenTrade recordFills(TradeId id, ExecutedEntries entries, MarketContext context) {
        Trade trade = require(id);
        if (!(trade instanceof PlannedTrade planned)) {
            throw wrongState(trade, "계획");
        }
        OpenTrade open = planned.fill(entries, context);
        saveTrade.save(open);
        return open;
    }

    @Override
    public ClosedTrade closeTrade(TradeId id, TradeClosure closure) {
        Trade trade = require(id);
        if (!(trade instanceof OpenTrade open)) {
            throw wrongState(trade, "체결됨");
        }
        ClosedTrade closed = open.close(closure);
        saveTrade.save(closed);
        // 저장이 끝난 뒤에 알린다. 듣는 쪽이 없어도 아무 일도 일어나지 않는 것이 정상이다 —
        // 이 모듈은 누가 듣는지 알지 못하고, 알아서도 안 된다(ADR 018 과 같은 이유의 반대 방향).
        events.publishEvent(new TradeClosedEvent(closed.id()));
        return closed;
    }

    @Override
    public Trade trade(TradeId id) {
        return require(id);
    }

    @Override
    public List<ClosedTrade> closedTrades(TradeQuery query) {
        return loadTrades.findClosed(query);
    }

    @Override
    public List<Trade> activeTrades() {
        return loadTrades.findActive();
    }

    /**
     * {@inheritDoc}
     *
     * <p>정렬과 필터링은 이미 포트의 계약이므로 여기서 다시 하지 않는다. 집계 자체는
     * 도메인이 한다 — 이 메서드에 합계를 내는 코드가 생기면 규칙이 샌 것이다.
     */
    @Override
    public JournalSummary summarize(TradeQuery query) {
        return JournalSummary.of(loadTrades.findClosed(query));
    }

    private Trade require(TradeId id) {
        return loadTrades.findById(id).orElseThrow(() -> new TradeNotFoundException(id));
    }

    private static InvalidTradeException wrongState(Trade trade, String expected) {
        return new InvalidTradeException(
                "%s 상태의 거래에만 할 수 있다. 지금은 %s 다: %s"
                        .formatted(expected, stateOf(trade), trade.id()));
    }

    /** sealed 라서 default 가 없다. 상태가 늘면 이 switch 가 컴파일 오류로 알려 준다. */
    private static String stateOf(Trade trade) {
        return switch (trade) {
            case PlannedTrade ignored -> "계획";
            case OpenTrade ignored -> "체결됨";
            case ClosedTrade ignored -> "청산됨";
        };
    }
}
